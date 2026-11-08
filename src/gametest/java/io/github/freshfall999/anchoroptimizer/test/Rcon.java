package io.github.freshfall999.anchoroptimizer.test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** Minimal Source RCON client, used by {@link ServerAnchorTest} to set up and inspect the test server. */
final class Rcon implements AutoCloseable {
	private static final int TYPE_COMMAND = 2;
	private static final int TYPE_LOGIN = 3;

	private final Socket socket;
	private final DataInputStream in;
	private final OutputStream out;
	private int nextId = 1;

	Rcon(String host, int port, String password) throws IOException {
		this.socket = new Socket(host, port);
		this.socket.setTcpNoDelay(true);
		this.in = new DataInputStream(socket.getInputStream());
		this.out = socket.getOutputStream();
		int id = send(TYPE_LOGIN, password);
		if (readPacketId() != id) {
			throw new IOException("RCON login failed");
		}
	}

	synchronized String command(String command) {
		try {
			send(TYPE_COMMAND, command);
			return readPayload();
		} catch (IOException e) {
			throw new RuntimeException("RCON command failed: " + command, e);
		}
	}

	private int send(int type, String payload) throws IOException {
		int id = nextId++;
		byte[] body = payload.getBytes(StandardCharsets.UTF_8);
		ByteBuffer buffer = ByteBuffer.allocate(14 + body.length).order(ByteOrder.LITTLE_ENDIAN);
		buffer.putInt(10 + body.length).putInt(id).putInt(type).put(body).put((byte) 0).put((byte) 0);
		out.write(buffer.array());
		out.flush();
		return id;
	}

	private byte[] readPacket() throws IOException {
		byte[] lengthBytes = new byte[4];
		in.readFully(lengthBytes);
		int length = ByteBuffer.wrap(lengthBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		byte[] packet = new byte[length];
		in.readFully(packet);
		return packet;
	}

	private int readPacketId() throws IOException {
		return ByteBuffer.wrap(readPacket()).order(ByteOrder.LITTLE_ENDIAN).getInt();
	}

	private String readPayload() throws IOException {
		byte[] packet = readPacket();
		return new String(packet, 8, packet.length - 10, StandardCharsets.UTF_8);
	}

	@Override
	public void close() throws IOException {
		socket.close();
	}
}
