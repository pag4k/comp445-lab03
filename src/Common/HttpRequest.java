package Common;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Optional;

public class HttpRequest extends HttpMessage {
	private static final int HTTP_PORT = 8080;
	private static final String DEFAULT_USER_AGENT_HEADER = "User-Agent: Concordia-HTTP/1.0";

	private Optional<EHttpOperation> HttpOperation;
	private boolean bVerbose;
	private Optional<String> BodyFromFile;
	private Optional<URL> Url;
	private Optional<String> Path;
	private Optional<String> InputFileName;
	private Optional<String> OutputFileName;

	public HttpRequest(String Arguments[]) {
		// Set default values.
		Error = Optional.empty();
		HttpOperation = Optional.empty();
		bVerbose = false;
		HeaderMap = new HashMap<String, String>();
		Body = Optional.empty();
		InputFileName = Optional.empty();
		BodyFromFile = Optional.empty();
		Url = Optional.empty();
		OutputFileName = Optional.empty();

		// Handle (get|post) which must be second argument.
		if (Arguments.length > 0) {
			try {
				HttpOperation = Optional.of(EHttpOperation.valueOf(Arguments[0].toLowerCase()));
			} catch (IllegalArgumentException e) {
				Error = Optional.of("ERROR: Operation " + Arguments[0] + " is not valid.");
				return;
			}
		}

		// Handle all other arguments.
		for (int i = 1; i < Arguments.length; ++i) {
			boolean bLastArgument = !((i + 1) < Arguments.length);
			if (Arguments[i].equals("-v")) {
				bVerbose = true;
			} else if (Arguments[i].startsWith("-")) {
				// Get the OptionString.
				final String OptionString = (Arguments[i].length() > 1) ? Arguments[i].substring(1) : "";
				// We assume that all these options require a parameter.
				// FIXME: Find a better way to deal with these options.
				if (bLastArgument && (OptionString.equals("h") || OptionString.equals("d") || OptionString.equals("f")
						|| OptionString.equals("o"))) {
					Error = Optional.of("ERROR: Option -" + OptionString + " requires a parameter.");
					return;
				}
				i++;
				final String ParameterString = Arguments[i];
				if (OptionString.equals("h")) {
					String[] Pair = ParameterString.split(":", 2);
					if (Pair.length == 2) {
						final String Key = Pair[0].trim();
						final String Value = Pair[1].trim();
						if (HeaderMap.containsKey(Key)) {
							Error = Optional.of("ERROR: Header key " + Key + " is duplicated.");
							return;
						} else {
							HeaderMap.put(Key, Value);
						}
					} else {
						Error = Optional.of("ERROR: Option -h has an invalid key:value pair: " + ParameterString + ".");
						return;
					}
				} else if (OptionString.equals("d")) {
					Body = Optional.of(ParameterString);
				} else if (OptionString.equals("f")) {
					InputFileName = Optional.of(ParameterString);
					try {
						BodyFromFile = Optional.of(new String(Files.readAllBytes(Paths.get(InputFileName.get()))));
					} catch (Exception e) {
						Error = Optional.of("ERROR: Could not read file " + InputFileName.get() + ".");
						return;
					}
				} else if (OptionString.equals("o")) {
					OutputFileName = Optional.of(ParameterString);
				} else {
					Error = Optional.of("ERROR: option -" + OptionString + " is unknown.");
					return;
				}
			} else {
				// We assume that anything else is an URL.
				try {
					Url = Optional.of(new URL(Arguments[i]));
				} catch (MalformedURLException e) {
					Error = Optional.of("ERROR: URL " + Arguments[i] + " is malformed.");
					return;
				}
			}
		}
	}

	public HttpRequest(BufferedReader BufferedReader) throws IOException {
		// Set default values.
		Error = Optional.empty();
		HttpOperation = Optional.empty();
		bVerbose = false;
		HeaderMap = new HashMap<String, String>();
		Body = Optional.empty();
		BodyFromFile = Optional.empty();
		Url = Optional.empty();
		Path = Optional.empty();
		OutputFileName = Optional.empty();
		HttpVersion = Optional.empty();

		String RequestLine = BufferedReader.readLine();

		if (RequestLine != null) {
			String[] Arguments = RequestLine.split(" ", 3);
			if (Arguments.length != 3) {
				Error = Optional.of("ERROR: Invalid RequestLine.");
				return;
			}
			try {
				HttpOperation = Optional.of(EHttpOperation.valueOf(Arguments[0].toLowerCase()));
			} catch (IllegalArgumentException e) {
				Error = Optional.of("ERROR: Operation " + Arguments[0] + " is not valid.");
				return;
			}
			Path = Optional.of(Arguments[1]);
			HttpVersion = Optional.of(Arguments[2]);
		} else {
			Error = Optional.of("ERROR: No RequestLine.");
			return;
		}

		String HeaderLine = null;
		while (!(HeaderLine = BufferedReader.readLine()).equals("")) {
			String[] Pair = HeaderLine.split(":", 2);
			if (Pair.length == 2) {
				final String Key = Pair[0].trim();
				final String Value = Pair[1].trim();
				if (HeaderMap.containsKey(Key)) {
					// FIXME: For now, just overwrite it, but it should not do that.
					HeaderMap.put(Key, Value);
					// Error = Optional.of("ERROR: Header key " + Key + " is duplicated.");
					// return;
				} else {
					HeaderMap.put(Key, Value);
				}
			} else {
				Error = Optional.of("ERROR: Invalid key:value pair: " + HeaderLine);
				return;
			}
		}

		if (HeaderMap.containsKey(CONTENT_LENGTH_HEADER)) {
			int ContentLength = -1;
			try {
				ContentLength = Integer.parseInt(HeaderMap.get(CONTENT_LENGTH_HEADER));
			} catch (NumberFormatException e) {
				Error = Optional.of("ERROR: Invalid Content-Length: " + HeaderMap.get(CONTENT_LENGTH_HEADER));
				return;
			}

			if (ContentLength >= 0) {
				if (ContentLength > 0) {
					char BodyArray[] = new char[ContentLength];
					final int BodyLength = BufferedReader.read(BodyArray, 0, ContentLength);
					if (BodyLength == ContentLength) {
						Body = Optional.of(new String(BodyArray));
					} else {
						Error = Optional.of(
								"ERROR: Content-Length is " + ContentLength + ", but the Body Length is " + BodyLength);
						return;
					}
				}
			} else {
				Error = Optional.of("ERROR: Content-Length must be greater than or equal to zero: "
						+ HeaderMap.get(CONTENT_LENGTH_HEADER));
				return;
			}
		} else {
			// FIXME: Find a way to verify this.
//			if (BufferedReader.read() != -1 && BufferedReader.read() != -1) {
//				Error = Optional.of("ERROR: There is no Content-Length, but there is a Body.");
//				return;
//			}
		}
	}

	public static Boolean IsFirstLine(String FirstLine) {
		// FIXME: I could do better check for example with version and path.
		final String[] Arguments = FirstLine.split(" ", 3);
		if (Arguments.length != 3) {
			return false;
		}
		try {
			EHttpOperation.valueOf(Arguments[0].toLowerCase());
		} catch (IllegalArgumentException e) {
			return false;
		}
		return true;
	}

	public void Redirect(String Location) {
		// Try to directly form an URL for the Location.
		try {
			Url = Optional.of(new URL(Location));
			return;
		} catch (MalformedURLException e1) {
			// Do nothing.
		}

		// If it could not be directly constructed, assume it is a just the file.
		try {
			Url = Optional.of(new URL(Url.get().getProtocol(), Url.get().getHost(), Location));
		} catch (MalformedURLException e) {
			Url = Optional.empty();
			Error = Optional.of("ERROR: Redirect location " + Location + " is invalid.");
		}
	}

	public boolean IsValid() {
		if (Error.isPresent()) {
			return false;
		}
		if (HttpOperation.isPresent()) {
			switch (HttpOperation.get()) {
			case get:
				if (!(Body.isEmpty() && BodyFromFile.isEmpty())) {
					Error = Optional.of("ERROR: GET operation cannot use the -d and -f options.");
					return false;
				}
				break;
			case post:
				if (!(Body.isPresent() ^ BodyFromFile.isPresent())) {
					Error = Optional.of("ERROR: POST operation must have either the -d or -f option, but not both.");
					return false;
				}
				break;
			}
		} else {
			return false;
		}

		if (Url.isEmpty() && Path.isEmpty()) {
			return false;
		}

		return true;
	}

	public byte[] GetAsBytes() {
		// FIXME: This is very inefficient.
		ArrayList<Byte> Bytes = new ArrayList<Byte>();

		Boolean bAfterHeader = false;
		for (String Line : GenAsLines()) {
			Bytes.addAll(Arrays.asList(ByteUtils.ToObjects(Line.getBytes())));
			if (!bAfterHeader) {
				Bytes.add((byte) '\r');
				Bytes.add((byte) '\n');
			}
			if (Line.equals("")) {
				bAfterHeader = true;
			}
		}

		return ByteUtils.ToPrimitives(Bytes.toArray());
	}

	public ArrayList<String> GenAsLines() {
		ArrayList<String> Output = new ArrayList<String>();

		// Remove Content-Length header.
		if (HeaderMap.containsKey(CONTENT_LENGTH_HEADER)) {
			HeaderMap.remove(CONTENT_LENGTH_HEADER);
		}

		switch (HttpOperation.get()) {
		case get:
			Output.add("GET " + (Url.get().getFile().isEmpty() ? "/" : Url.get().getFile()) + " HTTP/1.0");
			Output.add("Host: " + Url.get().getHost());
			for (String Key : HeaderMap.keySet()) {
				Output.add(Key + ": " + HeaderMap.get(Key));
			}
			if (!HeaderMap.containsKey("User-Agent")) {
				Output.add(DEFAULT_USER_AGENT_HEADER);
			}
			Output.add("");
			break;
		case post:
			// Get the body.
			String RawBody = null;
			if (Body.isPresent()) {
				RawBody = Body.get();
			} else if (BodyFromFile.isPresent()) {
				RawBody = BodyFromFile.get();
			} else {
				assert (true);
			}

			Output.add("POST " + (Url.get().getFile().isEmpty() ? "/" : Url.get().getFile()) + " HTTP/1.0");
			Output.add("Host: " + Url.get().getHost());
			if (RawBody != null) {
				Output.add(CONTENT_LENGTH_HEADER + ": " + RawBody.getBytes().length);
			}
			for (final String Key : HeaderMap.keySet()) {
				Output.add(Key + ": " + HeaderMap.get(Key));
			}
			if (!HeaderMap.containsKey("User-Agent")) {
				Output.add(DEFAULT_USER_AGENT_HEADER);
			}
			Output.add("");
			Output.add(RawBody);

			break;
		}

		return Output;
	}

	public Optional<HttpResponse> Send() throws IOException {
		Socket Socket;
		try {
			Socket = new Socket(Url.get().getHost(), HTTP_PORT);
		} catch (UnknownHostException e) {
			System.out.println("ERROR: Could not reach host " + Url.get().getHost());
			return Optional.empty();
		} catch (IOException e) {
			System.out.println("ERROR: Unknown IOException.");
			return Optional.empty();
		}

		OutputStream OutputStream = Socket.getOutputStream();
		PrintWriter PrintWriter = new PrintWriter(OutputStream, true);
		BufferedReader BufferedReader = new BufferedReader(new InputStreamReader(Socket.getInputStream()));

		final ArrayList<String> Request = GenAsLines();

		System.out.println("###### REQUEST #####");
		for (String Line : Request) {
			System.out.println(Line);
			PrintWriter.print(Line + "\r\n");
		}

		System.out.println("####################");
		System.out.println();

		PrintWriter.print("\r\n");
		PrintWriter.flush();

		String[] ResponseArray = HttpResponse.FromBufferedReader(BufferedReader);
		HttpResponse Response = new HttpResponse(ResponseArray);

		if (!Response.IsValid()) {
			if (Response.GetError().isPresent()) {
				System.out.println(Response.GetError().get());
			} else {
				System.out.println("ERROR: Unknown issue with HTTP Response.");
			}
			Socket.close();
			PrintWriter.close();
			OutputStream.close();
			BufferedReader.close();
			return Optional.empty();
		}

		String ResponseString = Response.toString(bVerbose).get();

		if (OutputFileName.isPresent()) {
			FileWriter OutputFileWriter = new FileWriter(OutputFileName.get());
			PrintWriter OutputPrintWriter = new PrintWriter(OutputFileWriter);
			ResponseString.lines().forEach(OutputPrintWriter::println);
			OutputPrintWriter.close();
			OutputFileWriter.close();
		} else {
			System.out.println("##### RESPONSE #####");
			ResponseString.lines().forEach(System.out::println);
			System.out.println("####################");
			System.out.println();
		}

		Socket.close();
		PrintWriter.close();
		OutputStream.close();
		BufferedReader.close();

		return Optional.of(Response);
	}

	public Optional<String> GetError() {
		return Error;
	}

	public String GetPath() {
		// Remove first '/'.
		return Path.get().length() > 0 ? Path.get().substring(1) : "";
	}

	public EHttpOperation GetOperation() {
		return HttpOperation.get();
	}

	public String GetBody() {
		return Body.isPresent() ? Body.get() : "";
	}

	public Optional<String> toString(boolean bVerbose) {

		StringBuilder ResponseBuilder = new StringBuilder();
		if (bVerbose) {
			ResponseBuilder.append(HttpOperation.get() + " " + Path.get() + " " + HttpVersion.get() + "\n");
			for (String Key : HeaderMap.keySet()) {
				ResponseBuilder.append(Key + ": " + HeaderMap.get(Key) + "\n");
			}
		}
		if (Body.isPresent()) {
			ResponseBuilder.append(Body.get());
		}

		return Optional.of(ResponseBuilder.toString());
	}
}
