package Common;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Optional;

public abstract class HttpMessage {

	protected static final String CONTENT_LENGTH_HEADER = "Content-Length";

	protected Optional<String> Error;
	protected Optional<String> HttpVersion;
	protected HashMap<String, String> HeaderMap;
	protected Optional<String> Body;

	abstract public byte[] GetAsBytes();

	static public Optional<HttpMessage> TryToParse(byte[] Bytes) {
		final Optional<String> FirstLine = GetFirstLine(Bytes);
		if (FirstLine.isPresent()) {
			if (HttpRequest.IsFirstLine(FirstLine.get())) {
				Reader StringReader = new StringReader(new String(Bytes));
				BufferedReader BufferedReader = new BufferedReader(StringReader);
				HttpRequest Request;
				try {
					Request = new HttpRequest(BufferedReader);
					if (Request.IsValid()) {
						return Optional.of(Request);
					}
				} catch (IOException e) {
				}

			} else if (HttpResponse.IsFirstLine(FirstLine.get())) {
				HttpResponse Response = new HttpResponse(new String(Bytes));
				if (Response.IsValid()) {
					return Optional.of(Response);
				}
			}
		}
		return Optional.empty();
	}

	static private Optional<String> GetFirstLine(byte[] Bytes) {
		for (int i = 0; i < Bytes.length; i++) {
			if (Bytes[i] == (byte) '\r' && i + 1 < Bytes.length && Bytes[i + 1] == (byte) '\n') {
				return Optional.of(new String(Arrays.copyOf(Bytes, i)));
			}
		}
		return Optional.empty();
	}
}
