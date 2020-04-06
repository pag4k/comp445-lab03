package Common;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class HttpResponse extends HttpMessage {
	private static final int[] REDIRECTED_CODES = { 300, 301, 302 };

	private static final Map<Integer, String> Phrases = Map.of(200, "OK", 201, "Created", 400, "Bad Request", 403,
			"Forbidden", 404, "Not found");

	private Optional<Integer> StatusCode;
	private Optional<String> Phrase;

	public HttpResponse(String[] Lines) {
		Error = Optional.empty();
		HttpVersion = Optional.empty();
		StatusCode = Optional.empty();
		Phrase = Optional.empty();
		HeaderMap = new HashMap<String, String>();
		Body = Optional.empty();

		if (Lines.length > 0) {
			String[] Arguments = Lines[0].split(" ", 3);
			if (Arguments.length != 3) {
				Error = Optional.of("ERROR: Invalid StatusLine.");
				return;
			}
			if (Arguments.length > 0) {
				HttpVersion = Optional.of(Arguments[0]);
			}
			if (Arguments.length > 1) {
				try {
					int Code = Integer.parseUnsignedInt(Arguments[1]);
					StatusCode = Optional.of(Code);
				} catch (NumberFormatException e) {
					Error = Optional.of("ERROR: Could not parse StatusCode: " + Arguments[1] + ".");
					return;
				}
			}
			if (Arguments.length > 2) {
				Phrase = Optional.of(Arguments[2]);
			}
		} else {
			Error = Optional.of("ERROR: No StatusLine.");
			return;
		}

		if (Lines.length == 1) {
			return;
		}

		int i = 1;
		while (!Lines[i].isEmpty()) {
			String[] Pair = Lines[i].split(":", 2);
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
				Error = Optional.of("ERROR: Invalid key:value pair: " + Lines[i]);
				return;
			}
			i++;
		}

		i++;

		// If we assume there is no \r\n in body, it takes one line.
		if (i < Lines.length) {
//			System.out.println("#########");
//			System.out.println(Lines[i]);
//			System.out.println("#########");
//			StringBuilder BodyBuilder = new StringBuilder();
//			for (; i < Lines.length; ++i) {
//				BodyBuilder.append(Lines[i] + "\n");
//			}
			Body = Optional.of(Lines[i]);
		}

		int ContentLength = -1;
		if (HeaderMap.containsKey(CONTENT_LENGTH_HEADER)) {
			try {
				ContentLength = Integer.parseInt(HeaderMap.get(CONTENT_LENGTH_HEADER));
			} catch (NumberFormatException e) {
				Error = Optional.of("ERROR: Invalid Content-Length: " + HeaderMap.get(CONTENT_LENGTH_HEADER));
				return;
			}
		}

		if (Body.isPresent()) {
			if (ContentLength != Body.get().length()) {
				Error = Optional.of(
						"ERROR: Content-Length is " + ContentLength + ", but body length is  " + Body.get().length());
				// System.out.println(Error.get());
				return;
			}
		} else {
			if (ContentLength != 0) {
				Error = Optional.of("ERROR: Content-Length should be 0, but is: " + ContentLength);
				return;
			}
		}
	}

	public HttpResponse(String Text) {
		this(Text.split("\r\n", -1));
	}

	public HttpResponse(int StatusCode, String Body) {
		Error = Optional.empty();
		HttpVersion = Optional.of("HTTP/1.0");
		this.StatusCode = Optional.of(StatusCode);
		Phrase = Optional.of(Phrases.get(StatusCode));
		HeaderMap = new HashMap<String, String>();
		HeaderMap.put("Content-Disposition", "attachment");
		HeaderMap.put("Connection", "closed");
		this.Body = Optional.of(Body);
		HeaderMap.put("Content-Length", Integer.toString(Body.length()));
	}

	public HttpResponse(int StatusCode, String Body, String FileType) {
		Error = Optional.empty();
		HttpVersion = Optional.of("HTTP/1.0");
		this.StatusCode = Optional.of(StatusCode);
		Phrase = Optional.of(Phrases.get(StatusCode));
		HeaderMap = new HashMap<String, String>();
		HeaderMap.put("Content-Type", FileType);
		// Assume FileType is not null and is of a valid form.
		String[] Types = FileType.split("/");
		// Only show inline 'text' type.
		if (Types[0].equalsIgnoreCase("text")) {
			HeaderMap.put("Content-Disposition", "inline");
		} else {
			HeaderMap.put("Content-Disposition", "attachment");
		}
		HeaderMap.put("Connection", "closed");
		this.Body = Optional.of(Body);
		HeaderMap.put("Content-Length", Integer.toString(Body.length()));
	}

	public static Boolean IsFirstLine(String FirstLine) {
		// FIXME: I could do better check for example with version.
		final String[] Arguments = FirstLine.split(" ", 3);
		if (Arguments.length != 3) {
			return false;
		}
		try {
			int Code = Integer.parseUnsignedInt(Arguments[1]);
			return Phrases.containsKey(Code);
		} catch (NumberFormatException e) {
			return false;
		}
	}

	public boolean IsValid() {
		if (Error.isPresent()) {
			return false;
		}

		if (HttpVersion.isEmpty() || StatusCode.isEmpty() || Phrase.isEmpty()) {
			return false;
		}

		return true;
	}

	public byte[] GetAsBytes() {
		// FIXME: This is very inefficient.
		ArrayList<Byte> Bytes = new ArrayList<Byte>();

		// FIXME: Assume it is a valid Response.
		Bytes.addAll(Arrays.asList(ByteUtils.ToObjects(toString(true).get().getBytes())));

		return ByteUtils.ToPrimitives(Bytes.toArray());
	}

	public Optional<String> toString(boolean bVerbose) {
		if (!IsValid()) {
			return Optional.empty();
		}

		StringBuilder ResponseBuilder = new StringBuilder();
		if (bVerbose) {
			ResponseBuilder.append(HttpVersion.get() + " " + StatusCode.get() + " " + Phrase.get() + "\r\n");
			for (String Key : HeaderMap.keySet()) {
				ResponseBuilder.append(Key + ": " + HeaderMap.get(Key) + "\r\n");
			}
		}
		ResponseBuilder.append("\r\n");
		if (Body.isPresent()) {
			ResponseBuilder.append(Body.get());
		}

		return Optional.of(ResponseBuilder.toString());
	}

	static public String[] FromBufferedReader(BufferedReader BufferedReader) throws IOException {
		ArrayList<String> ResponseLines = new ArrayList<String>();
		String Line;
		while ((Line = BufferedReader.readLine()) != null) {
			ResponseLines.add(Line);
		}

		String[] ResponseArray = new String[ResponseLines.size()];
		ResponseLines.toArray(ResponseArray);

		return ResponseArray;

	}

	public boolean IsRedirect() {
		return Arrays.stream(REDIRECTED_CODES).anyMatch(StatusCode.get()::equals);
	}

	public Optional<String> GetRedirectLocation() {
		if (HeaderMap.containsKey("Location")) {
			return Optional.of(HeaderMap.get("Location"));
		} else {
			return Optional.empty();
		}
	}

	public Optional<String> GetError() {
		return Error;
	}

	public static String GetPhrase(int StatusCode) {
		return Phrases.containsKey(StatusCode) ? Phrases.get(StatusCode) : "";
	}
}
