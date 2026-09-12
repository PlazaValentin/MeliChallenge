package com.hackerrank.challenge.api.error;

import com.hackerrank.challenge.domain.exception.BusinessRuleException;
import com.hackerrank.challenge.domain.exception.DomainValidationException;
import com.hackerrank.challenge.domain.exception.ResourceNotFoundException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Traduce a respuesta HTTP toda excepcion que escape de la aplicacion, en un
 * unico lugar: validacion de entrada 400, recurso inexistente 404, regla de
 * negocio 409, y cualquier otra 500 (ver DECISIONS.md).
 *
 * <p>
 * Al cliente se le da el minimo necesario para mostrar el error en un pop-up;
 * el
 * detalle tecnico queda solo en el log, y el log no registra informacion
 * personal.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  private static final String INVALID_INPUT = "Hay datos invalidos en la solicitud.";

  /** Dato de entrada mal formado o que no cumple una invariante del dominio. */
  @ExceptionHandler(DomainValidationException.class)
  public ResponseEntity<ErrorResponse> handleValidation(DomainValidationException exception) {
    return respond(HttpStatus.BAD_REQUEST, exception.getMessage());
  }

  /**
   * Violaciones de las anotaciones de validacion sobre path variables y query
   * params. Se listan todas juntas en {@code errors} en vez de cortar en la
   * primera, para que el frontend pueda marcar todos los campos de una vez.
   */
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ErrorResponse> handleConstraintViolation(
      ConstraintViolationException exception) {

    List<ErrorResponse.FieldError> errors = exception.getConstraintViolations().stream()
        .map(violation -> new ErrorResponse.FieldError(
            lastNodeOf(violation), violation.getMessage()))
        .toList();

    return ResponseEntity.badRequest()
        .body(ErrorResponse.of(HttpStatus.BAD_REQUEST.value(), INVALID_INPUT, errors));
  }

  /** Violaciones de las anotaciones de validacion sobre un cuerpo de request. */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleInvalidBody(
      MethodArgumentNotValidException exception) {

    List<ErrorResponse.FieldError> errors = exception.getBindingResult().getFieldErrors().stream()
        .map(this::toFieldError)
        .toList();

    return ResponseEntity.badRequest()
        .body(ErrorResponse.of(HttpStatus.BAD_REQUEST.value(), INVALID_INPUT, errors));
  }

  /**
   * Un path variable o query param que no se puede convertir al tipo esperado:
   * UUID mal formado, o un estado que no forma parte del ciclo de vida. Es un
   * error de la consulta, no un recurso inexistente, asi que va 400 y no 404.
   */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> handleTypeMismatch(
      MethodArgumentTypeMismatchException exception) {

    String field = exception.getName();
    ErrorResponse body = ErrorResponse.of(
        HttpStatus.BAD_REQUEST.value(),
        INVALID_INPUT,
        List.of(new ErrorResponse.FieldError(field, describeMismatch(exception))));

    log.warn("Parametro invalido '{}' en la consulta.", field);
    return ResponseEntity.badRequest().body(body);
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<ErrorResponse> handleMissingParameter(
      MissingServletRequestParameterException exception) {

    ErrorResponse body = ErrorResponse.of(
        HttpStatus.BAD_REQUEST.value(),
        INVALID_INPUT,
        List.of(new ErrorResponse.FieldError(
            exception.getParameterName(), "Es obligatorio.")));

    return ResponseEntity.badRequest().body(body);
  }

  /** Id sintacticamente valido pero que no corresponde a ningun recurso. */
  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException exception) {
    return respond(HttpStatus.NOT_FOUND, exception.getMessage());
  }

  /** El recurso existe, pero la operacion no es admisible en su estado actual. */
  @ExceptionHandler(BusinessRuleException.class)
  public ResponseEntity<ErrorResponse> handleBusinessRule(BusinessRuleException exception) {
    return respond(HttpStatus.CONFLICT, exception.getMessage());
  }

  /**
   * Red de contencion. El mensaje real no se le devuelve al cliente: puede
   * contener detalle tecnico que no corresponde exponer, asi que se loguea
   * completo y afuera sale un texto generico.
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception) {
    log.error("Error no contemplado procesando la solicitud.", exception);
    return respond(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Ocurrio un error inesperado. Volve a intentar en unos minutos.");
  }

  /**
   * Describe que se esperaba sin nombrar el valor recibido, para no reflejar de
   * vuelta una entrada arbitraria del cliente.
   */
  private String describeMismatch(MethodArgumentTypeMismatchException exception) {
    return enumTypeOf(exception)
        .map(enumType -> "Debe ser uno de: " + String.join(", ", enumValueNames(enumType)) + ".")
        .orElse("No tiene el formato esperado.");
  }

  /**
   * Resuelve el enum esperado, ya sea porque el parametro es el enum en si o
   * porque es una coleccion de ese enum: en un filtro repetible como
   * {@code ?status=...} el tipo declarado es la coleccion, y quedarse con eso
   * perderia el unico dato util para el mensaje.
   */
  private Optional<Class<?>> enumTypeOf(MethodArgumentTypeMismatchException exception) {
    Class<?> requiredType = exception.getRequiredType();
    if (requiredType != null && requiredType.isEnum()) {
      return Optional.of(requiredType);
    }

    MethodParameter parameter = exception.getParameter();
    ResolvableType[] generics = ResolvableType.forMethodParameter(parameter).getGenerics();
    if (generics.length != 1) {
      return Optional.empty();
    }

    Class<?> itemType = generics[0].resolve();
    return itemType != null && itemType.isEnum()
        ? Optional.<Class<?>>of(itemType)
        : Optional.empty();
  }

  private List<String> enumValueNames(Class<?> enumType) {
    return Arrays.stream(enumType.getEnumConstants())
        .map(constant -> ((Enum<?>) constant).name())
        .toList();
  }

  private ErrorResponse.FieldError toFieldError(FieldError fieldError) {
    return new ErrorResponse.FieldError(fieldError.getField(), fieldError.getDefaultMessage());
  }

  /**
     * Se queda con el ultimo tramo del path de la violacion: el nombre del parametro
     * viene precedido por el del metodo del controller, que no le dice nada al
     * cliente.
     */
    private String lastNodeOf(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        int lastSeparator = path.lastIndexOf('.');
        return lastSeparator < 0 ? path : path.substring(lastSeparator + 1);
    }

  
    ate ResponseEntity<ErrorResp
        esponseEntity.status(status)
        .body(ErrorResponse.of(status.value(), de
  }

  
   
   
   
   
   
  
    
    
      
    

    
    
    
      
    

    
    
        
        
  

  
    
        
        
  

  
    
  

  
   
   * 
   
   
   
  
    
    
    
  

  
    
        
  