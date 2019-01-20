const express = require('express');
const app = express();
const server = require('http').Server(app);
const io = require('socket.io')(server);
const W3CWebSocket = require('websocket').w3cwebsocket;

if(process.argv.length != 3) {
	console.log('Missing argument <ip[:port]>');
	return;
}

let targetAddr = process.argv[2];
if(targetAddr.lastIndexOf(':') == -1) {
	targetAddr = targetAddr+':5001';
}

io.on('connection', (socket) => {
	console.log('Connection from '+socket.handshake.address);
	var client = null;
	var requestQueue = [];
	var responseQueue = [];
	var listeners = {};

	const processRequests = () => {
		if(client != null) {
			if(client.readyState == client.OPEN) {
				while(requestQueue.length > 0) {
					let req = requestQueue.shift();
					if(req.ack) {
						client.send(req.event);
						responseQueue.push(req.ack);
					} else {
						client.send(req.data);
					}
				}
			} else if(client.readyState == client.CONNECTING) {
				setImmediate(() => processRequests());
			}
		} else if(client == null) {
			client = new W3CWebSocket(`ws://${targetAddr}/`, null)

			client.onopen = () => {
				console.log('Connected to '+targetAddr);
				processRequests();
			};

			client.onmessage = (e) => {
				let json = JSON.parse(e.data);
				console.log('Response: '+JSON.stringify(json));
				let notif = json.notification;
				if(notif) {
					socket.emit(notif, json.data);
				} else {
					let ack = responseQueue.shift();
					ack(json);
				}
			};

			client.onclose = () => {
				console.log('Disconnected from '+targetAddr);
				Object.entries(listeners).forEach(([evt,l]) => {socket.off(evt, l);});
				client = null;
				processRequests();
			};

			client.onerror = (e) => {
				console.log('Connection error to '+targetAddr);
			};
		}
	};

	const forwardWithReply = (req) => {
		const l = (ack) => {
			console.log('Request: '+req)
			requestQueue.push({'event': req, 'ack': ack});
			processRequests();
		};
		socket.on(req, l);
		listeners[req] = l;
	};

	const forward = (req) => {
		const l = (data) => {
			console.log('Request: '+req+' '+data)
			requestQueue.push({'event': req, 'data': data});
			processRequests();
		};
		socket.on(req, l);
		listeners[req] = l;
	};

	forwardWithReply('get_version');
	forwardWithReply('get_settings');
	forwardWithReply('get_timestamp');

	forward('set_calibration_threshold');
	forward('set_calibration_offset');
	forward('set_trigger_threshold');
	forward('set_frequency');

	forward('reset_auto_calibration');

	socket.on('disconnect', (reason) => {
		console.log('Disconnected by '+socket.handshake.address);
		if(client) {
			client.close();
			client = null;
		}
	});

	socket.on('error', (error) => {
		console.log('Connection error to '+socket.handshake.address);
		if(client) {
			client.close();
			client = null;
		}
	});
});

server.listen(5000);
