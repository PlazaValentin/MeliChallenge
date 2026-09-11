# Project Files Instructions

## Project Structure

This is a fullstack project with a **Java (Gradle / Spring Boot)** backend and a **frontend of your choice**.

- `backend/` - Spring Boot application running on port **8080**
- `frontend/` - Your frontend application running on port **3000**

## Frontend Setup

You are free to choose any JavaScript framework or library (React, Angular, Vue, Svelte, vanilla JS, etc.) to build the frontend.

### Dev Server Requirements

For the frontend to render correctly in this environment, your dev server **must** be configured with:

1. **Port 3000** - The preview panel connects to port 3000.
2. **Host 0.0.0.0** - The server must listen on all network interfaces, not just localhost.
3. **Disable host header validation** - The environment accesses your app through a proxy domain (not localhost), so the dev server must accept requests from any host.

### How to configure (examples by framework)

| Framework | `client` script |
|-----------|----------------|
| React (CRA) | `PORT=3000 HOST=0.0.0.0 DANGEROUSLY_DISABLE_HOST_CHECK=true react-scripts start` |
| React (Vite) | `vite --host 0.0.0.0 --port 3000` |
| Angular | `ng serve --host 0.0.0.0 --port 3000 --disable-host-check` |
| Vue (Vite) | `vite --host 0.0.0.0 --port 3000` |
| Svelte (Vite) | `vite --host 0.0.0.0 --port 3000` |

Update the `"client"` script in `frontend/package.json` with the appropriate command for your chosen framework.

### Connecting to the Backend API

The backend runs on port **8080**. In this environment, the URL is not `localhost:8080`. You must derive the backend URL from the current browser location by replacing `3000` with `8080`:

```javascript
const currentHost = window.location.host;       // e.g. vm-xxx-3000.hrcdn.net
const backendHost = currentHost.replace("3000", "8080"); // e.g. vm-xxx-8080.hrcdn.net
const baseURL = `${window.location.protocol}//${backendHost}`;
```

### Running the project

- **Install**: `npm install` (from the `frontend/` directory)
- **Start**: `npm start` (starts both backend and frontend concurrently)
- **Test**: `npm test`
