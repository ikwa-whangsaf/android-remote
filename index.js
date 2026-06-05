const http = require("http");
const { WebSocketServer } = require("ws");
const fs = require("fs");
const path = require("path");

const PORT = process.env.PORT || 4101;

// ==================
// HTML PAGES
// ==================
const controllerHTML = fs.existsSync(path.join(__dirname, "controller.html"))
  ? fs.readFileSync(path.join(__dirname, "controller.html"), "utf8")
  : "<h1>controller.html not found</h1>";

const receiverHTML = fs.existsSync(path.join(__dirname, "receiver.html"))
  ? fs.readFileSync(path.join(__dirname, "receiver.html"), "utf8")
  : "<h1>receiver.html not found</h1>";

// ==================
// HTTP SERVER
// ==================
const server = http.createServer((req, res) => {
  const url = req.url.split("?")[0];

  if (url === "/" || url === "/controller") {
    res.writeHead(200, { "Content-Type": "text/html; charset=utf-8" });
    res.end(controllerHTML);
  } else if (url === "/receiver") {
    res.writeHead(200, { "Content-Type": "text/html; charset=utf-8" });
    res.end(receiverHTML);
  } else {
    res.writeHead(404, { "Content-Type": "text/plain" });
    res.end("Not found");
  }
});

// ==================
// WEBSOCKET SERVER
// ==================
const wss = new WebSocketServer({ server });

let controller = null;
let receiver = null;

wss.on("connection", (ws) => {
  console.log("[+] Client connected");

  ws.on("message", (raw) => {
    let msg;
    try { msg = JSON.parse(raw); } catch { return; }

    if (msg.type === "register") {
      if (msg.role === "controller") {
        controller = ws;
        console.log("[*] Controller registered");
        ws.send(JSON.stringify({ type: "registered", role: "controller" }));
        ws.send(JSON.stringify({
          type: "receiver_status",
          online: receiver !== null && receiver.readyState === 1
        }));
      } else if (msg.role === "receiver") {
        receiver = ws;
        console.log("[*] Receiver registered");
        ws.send(JSON.stringify({ type: "registered", role: "receiver" }));
        if (controller && controller.readyState === 1) {
          controller.send(JSON.stringify({ type: "receiver_status", online: true }));
        }
      }
      return;
    }

    if (msg.type === "command") {
      if (receiver && receiver.readyState === 1) {
        receiver.send(JSON.stringify(msg));
        console.log("[>] Command:", msg.action);
      } else {
        ws.send(JSON.stringify({ type: "error", msg: "Receiver offline" }));
      }
      return;
    }

    if (msg.type === "status" || msg.type === "response") {
      if (controller && controller.readyState === 1) {
        controller.send(JSON.stringify(msg));
      }
      return;
    }
  });

  ws.on("close", () => {
    if (ws === controller) {
      controller = null;
      console.log("[-] Controller disconnected");
    }
    if (ws === receiver) {
      receiver = null;
      console.log("[-] Receiver disconnected");
      if (controller && controller.readyState === 1) {
        controller.send(JSON.stringify({ type: "receiver_status", online: false }));
      }
    }
  });
});

server.listen(PORT, () => {
  console.log(`[SERVER] Running on port ${PORT}`);
  console.log(`[SERVER] Controller → http://IP:${PORT}/controller`);
  console.log(`[SERVER] Receiver   → http://IP:${PORT}/receiver`);
});
