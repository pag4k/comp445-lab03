package Server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.channels.DatagramChannel;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;

import Common.Constants;
import Common.DatagramChannelUtils;
import Common.UdpMessage;

public class HttpFS {

	private final static Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

	private static final int SERVER_CLIENT_LIMIT = 10;

	public static void main(String[] args) {

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

		ServerConnection ServerConnections[] = new ServerConnection[SERVER_CLIENT_LIMIT];
		Thread Threads[] = new Thread[SERVER_CLIENT_LIMIT];

		try (DatagramChannel Channel = DatagramChannel.open();) {
			Channel.configureBlocking(false);
			// FIXME: Use port from option.
			Channel.bind(new InetSocketAddress(Constants.SERVER_ADDRESS.getPort()));
			if (Option.bVerbose) {
				LOGGER.log(Level.INFO, "Server started on port : " + Constants.SERVER_ADDRESS.getPort() + ".");
				LOGGER.log(Level.INFO, "Up to " + SERVER_CLIENT_LIMIT + " clients handled simultaneously.");
			}

			LOGGER.log(Level.INFO, "Waiting for SYN on " + Constants.SERVER_ADDRESS.toString() + "...");
			while (true) {
				// Get next connection.
				Optional<UdpMessage> SynMsg = DatagramChannelUtils.ReceiveOnce(Channel);

				// Check if any Thread is dead and clean up.
				for (int i = 0; i < SERVER_CLIENT_LIMIT; ++i) {
					if (Threads[i] != null && !Threads[i].isAlive()) {
						ServerConnections[i] = null;
						Threads[i] = null;
						if (Option.bVerbose) {
							LOGGER.log(Level.INFO, "Connecton closed with client #" + i + ".");
						}
					}
				}

				if (SynMsg.isPresent()) {
					// Check if repeated SYN.
					Boolean bDrop = false;
					for (int i = 0; i < SERVER_CLIENT_LIMIT; ++i) {
						if (Threads[i] != null) {
							LOGGER.log(Level.INFO,
									"Duplicate SYN detected: " + SynMsg.get().toString() + ". Dropping...");
							if (ServerConnections[i].IsSameAddree(SynMsg.get().GetSocketAddress())) {
								bDrop = true;
								break;
							}
						}
					}
					// Drop duplicate.
					if (bDrop) {
						continue;
					}
					// Check if there is a free thread.
					final int FreeThreadIndex = IntStream.range(0, SERVER_CLIENT_LIMIT).filter(i -> Threads[i] == null)
							.findFirst().orElse(-1);
					// If so, accept connection and send to SocketHandler.
					if (FreeThreadIndex >= 0) {
						if (SynMsg.get().IsSyn()) {
							LOGGER.log(Level.INFO, "Received: " + SynMsg.get()
									+ ". Launching connection thread for client " + FreeThreadIndex + "...");

							ServerConnection ServerConnection = new ServerConnection(SynMsg.get(), Option.Path);
							ServerConnections[FreeThreadIndex] = ServerConnection;
							Threads[FreeThreadIndex] = new Thread(ServerConnection);
							Threads[FreeThreadIndex].start();
						}
					} else {
						// Otherwise, refuse connection.
						if (Option.bVerbose) {
							LOGGER.log(Level.INFO, "Connecton refused from: " + SynMsg.toString());
						}
					}
				}
			}
		} catch (SocketException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
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
