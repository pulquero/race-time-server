const express = require('express');
const app = express();
const server = require('http').Server(app);
const io = require('socket.io')(server);
const W3CWebSocket = require('websocket').w3cwebsocket;

if(process.argv.length != 3) {
	console.log('Missing argument <ip:port>');
	return;
}

let targetAddr = process.argv[2];

client = new W3CWebSocket(`ws://${targetAddr}/`, null)
client.onopen = () => {
	io.on('connection', (socket) => {
		console.log('Connected');
		var requestsQueue = [];

		client.onmessage = (e) => {
			let json = JSON.parse(e.data);
			let notif = json.notification;
			if(notif) {
				socket.emit(notif, json.data);
			} else {
				let ack = requestsQueue.shift();
				ack(json);
			}
		};

		const forwardWithReply = (req) => {
			socket.on(req, (ack) => {
				client.send(req);
				requestsQueue.push(ack);
			});
		};

		const forward = (req) => {
			socket.on(req, (data) => {
				client.send(data);
			});
		};

		forwardWithReply('get_version');
		forwardWithReply('get_settings');
		forwardWithReply('get_timestamp');

		forward('set_calibration_threshold');
		forward('set_calibration_offset');
		forward('set_trigger_threshold');
		forward('set_frequency');

		forward('reset_auto_calibration');
	});

	server.listen(5000);
};

client.onerror = (e) => {
	console.log('Connection error');
};
