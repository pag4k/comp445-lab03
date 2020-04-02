package Server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.stream.IntStream;

import Common.UdpMessage;

public class HttpFS {

	private static final int SERVER_CLIENT_LIMIT = 10;

	private static final int SERVER_PORT = 8007;

	public static void main(String[] args) {

		try {
			DatagramChannel InChannel = DatagramChannel.open();
			InChannel.configureBlocking(false);
			// Port 0 will select any available one.
			InChannel.bind(new InetSocketAddress(SERVER_PORT));
			// SocketAddress Server = new InetSocketAddress("0.0.0.0", 3001);
			while (true) {
				ByteBuffer Buffer = ByteBuffer.allocate(UdpMessage.UDP_MESSAGE_MAX_SIZE);
				InetSocketAddress SocketAddress = (InetSocketAddress) InChannel.receive(Buffer);
				if (SocketAddress != null) {
					int a = 5;
					Buffer.clear();
					Optional<UdpMessage> Msg = UdpMessage.ConstructFromBytes(Buffer.array());
					if (Msg.isPresent()) {
						System.out.print(Msg.get().toString());

						// Msg.get().PrintAsUnsignedBytes();
					}
					// System.exit(0);
				}
			}
		} catch (SocketException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		FSOption Option = new HttpFS.FSOption(args);
		if (Option.Error.isPresent()) {
			System.out.println(Option.Error.get());
			System.out.println();
			PrintHelp();
			return;
		}

		if (!Files.exists(Option.Path) || !Files.isDirectory(Option.Path)) {
			System.out.println("ERROR: Path '" + Option.Path + "' does not exist or is not a directory.");
			System.out.println();
			PrintHelp();
			return;
		}

		Socket ClientSockets[] = new Socket[SERVER_CLIENT_LIMIT];
		Thread Threads[] = new Thread[SERVER_CLIENT_LIMIT];

		try (ServerSocket ServerSocket = new ServerSocket(Option.Port, SERVER_CLIENT_LIMIT)) {
			if (Option.bVerbose) {
				System.out.println("Server started on port : " + Option.Port + ".");
				System.out.println("Up to " + SERVER_CLIENT_LIMIT + " clients handled simultaneously.");
			}

			// FIXME: Add way to quit?
			while (true) {
				// Get next connection.
				Socket ClientSocket = ServerSocket.accept();

				// Check if any Thread is dead and clean up.
				for (int i = 0; i < SERVER_CLIENT_LIMIT; ++i) {
					if (Threads[i] != null && !Threads[i].isAlive()) {
						ClientSockets[i].close();
						ClientSockets[i] = null;
						Threads[i] = null;
						if (Option.bVerbose) {
							System.out.println("Connecton closed with client #" + i + ".");
						}
					}
				}

				// Check if there is a free thread.
				final int FreeThreadIndex = IntStream.range(0, SERVER_CLIENT_LIMIT).filter(i -> Threads[i] == null)
						.findFirst().orElse(-1);
				// If so, accept connection and send to SocketHandler.
				if (FreeThreadIndex >= 0) {
					HttpProtocol Server = new HttpProtocol(ClientSocket, Option.Path, Option.bVerbose);
					if (Option.bVerbose) {
						System.out.println("Connecton opened with client #" + FreeThreadIndex + ": "
								+ ClientSocket.toString() + ".");
					}
					ClientSockets[FreeThreadIndex] = ClientSocket;
					Threads[FreeThreadIndex] = new Thread(Server);
					Threads[FreeThreadIndex].start();
				} else {
					// Otherwise, refuse connection.
					ClientSocket.close();
					if (Option.bVerbose) {
						System.out.println("Connecton refused with " + ClientSocket.toString() + ".");
					}
				}
			}
		} catch (IOException e) {
			System.out.println("ERROR: Could not create ServerSocket: " + e.getMessage());
		}
//		try {
//			Threads[0].join();
//		} catch (InterruptedException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
	}

	public static void PrintHelp() {
		System.out.println("httpfs is a simple file server.\n" + "" + "usage: httpfs [-v] [-p PORT] [-d PATH-TO-DIR]\n"
				+ "" + "    -v Prints debugging messages." + ""
				+ "    -p Specifies the port number that the server will listen and serve at. Default is 8080." + ""
				+ "    -d Specifies the directory that the server will use to read/write requested files. Default is the current directory when launching the application.");
	}

	static private class FSOption {
		private static int DEFAULT_PORT = 8080;

		public boolean bVerbose;
		public Integer Port;
		public Path Path;
		public Optional<String> Error;

		public FSOption(String[] Arguments) {
			bVerbose = false;
			Port = DEFAULT_PORT;
			Path = Paths.get("").toAbsolutePath();
			Error = Optional.empty();

			System.out.print(Path.toString());

			for (int i = 0; i < Arguments.length; ++i) {
				boolean bLastArgument = !((i + 1) < Arguments.length);
				if (Arguments[i].equals("-v")) {
					bVerbose = true;
				} else if (Arguments[i].startsWith("-")) {
					// Get the OptionString.
					final String OptionString = (Arguments[i].length() > 1) ? Arguments[i].substring(1) : "";
					// We assume that all these options require a parameter.
					// FIXME: Find a better way to deal with these options.
					if (bLastArgument && (OptionString.equals("p") || OptionString.equals("d"))) {
						Error = Optional.of("ERROR: Option -" + OptionString + " requires a parameter.");
						return;
					}
					i++;
					final String ParameterString = Arguments[i];
					if (OptionString.equals("p")) {
						try {
							Port = Integer.parseInt(ParameterString);
						} catch (NumberFormatException e) {
							Error = Optional.of("ERROR: Invalid Port: " + ParameterString + ".");
							return;
						}
						if (0 > Port && Port > 65535) {
							Error = Optional.of("ERROR: Port " + Port + " is not within [0,65535].");
							Port = -1;
							return;
						}
					} else if (OptionString.equals("d")) {
						try {
							Path = Paths.get(ParameterString);
						} catch (InvalidPathException e) {
							Error = Optional.of("ERROR: Invalid path: " + ParameterString + ".");
							return;
						}
					} else {
						Error = Optional.of("ERROR: option -" + OptionString + " is unknown.");
						return;
					}
				} else {
					Error = Optional.of("ERROR: Invalid argument: " + Arguments[i] + ".");
					return;
				}
			}
		}
	}
}
