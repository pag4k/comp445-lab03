package Server;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import Common.HttpRequest;
import Common.HttpResponse;

public class HttpProtocol implements Runnable {

	final static String TEXT_HTML_FILE_TYPE = "text/html";
	final static int MAX_DELAY = 200;

	final private Socket ClientSocket;
	final private Path RootPath;
	final private Boolean bVerbose;

	public HttpProtocol(Socket ClientSocket, Path RootPath, Boolean bVerbose) {
		this.ClientSocket = ClientSocket;
		this.RootPath = RootPath;
		this.bVerbose = bVerbose;
	}

	@Override
	public void run() {

		try (BufferedReader BufferedReader = new BufferedReader(new InputStreamReader(ClientSocket.getInputStream()));
				PrintWriter PrintWriter = new PrintWriter(ClientSocket.getOutputStream());
				BufferedOutputStream BufferedOutputStream = new BufferedOutputStream(ClientSocket.getOutputStream());) {

			// Parse Request out of BufferedReader.
			HttpRequest Request = new HttpRequest(BufferedReader);

			HttpResponse Response = GetResponse(RootPath, Request, bVerbose);

			if (Response.GetError().isEmpty()) {
				if (bVerbose) {
					System.out.println(Response.toString(true).get());
				}
				PrintWriter.print(Response.toString(true).get());
				PrintWriter.flush();
			} else {
				System.out.println(Response.GetError().get());
			}

			// String RequestLine = BufferedReader.readLine();
		} catch (IOException e) {
			System.out.println("Server error : " + e);
		}
	}

	private static String GenerateDirectoryHtml(Path RootPath, Path FullPath) {
		StringBuilder StringBuilder = new StringBuilder();

		final String Title = "Directory listing for /" + RootPath.relativize(FullPath).toString();

		StringBuilder.append("<!DOCTYPE html>\n");
		StringBuilder.append("<html>\n");
		StringBuilder.append("<head>\n");
		StringBuilder.append("<title>" + Title + "</title>\n");
		StringBuilder.append(" <!DOCTYPE html>\n");
		StringBuilder.append("</head>\n");
		StringBuilder.append("<body>\n");
		StringBuilder.append("<h1>" + Title + "</h1>\n");
		StringBuilder.append("<hr>\n");
		StringBuilder.append("<ul>\n");
		try {
			List<Path> Paths = Files.list(FullPath).collect(Collectors.toList());
			for (Path CurrentPath : Paths) {
				final Path RelativePath = RootPath.relativize(CurrentPath);
				StringBuilder.append("<li><a href=\"" + RelativePath.toString() + "\">" + RelativePath.getFileName()
						+ "</a></li>\n");
			}
		} catch (IOException e) {
			// Just skip if there is an exception.
		}
		StringBuilder.append("</ul>\n");
		StringBuilder.append("<hr>\n");
		StringBuilder.append("</body>\n");
		StringBuilder.append("</html>\n");

		return StringBuilder.toString();
	}

	private static String GeneratePostHtml(Path RootPath, Path FullPath) {
		StringBuilder StringBuilder = new StringBuilder();

		final String Title = "File posted";

		StringBuilder.append("<!DOCTYPE html>\n");
		StringBuilder.append("<html>\n");
		StringBuilder.append("<head>\n");
		StringBuilder.append("<title>" + Title + "</title>\n");
		StringBuilder.append(" <!DOCTYPE html>\n");
		StringBuilder.append("</head>\n");
		StringBuilder.append("<body>\n");
		StringBuilder.append("<h1>" + Title + "</h1>\n");
		final Path RelativePath = RootPath.relativize(FullPath);
		StringBuilder.append("Click <a href=\"" + RelativePath + "\">" + RelativePath + "</a> to view it.");
		StringBuilder.append("</body>\n");
		StringBuilder.append("</html>\n");

		return StringBuilder.toString();
	}

	private static String GenerateErrorHtml(Path RootPath, Path FullPath, int StatusCode) {
		StringBuilder StringBuilder = new StringBuilder();

		final String Title = StatusCode + " " + HttpResponse.GetPhrase(StatusCode);

		StringBuilder.append("<!DOCTYPE html>\n");
		StringBuilder.append("<html>\n");
		StringBuilder.append("<head>\n");
		StringBuilder.append("<title>" + Title + "</title>\n");
		StringBuilder.append(" <!DOCTYPE html>\n");
		StringBuilder.append("</head>\n");
		StringBuilder.append("<body>\n");
		StringBuilder.append("<h1>" + Title + "</h1>\n");
		StringBuilder.append(GetErrorMessage(StatusCode, RootPath.relativize(FullPath).toString()));
		StringBuilder.append("</body>\n");
		StringBuilder.append("</html>\n");

		return StringBuilder.toString();
	}

	private static String GetErrorMessage(int StatusCode, String RelativePath) {
		if (StatusCode == 400) {
			return "Server doesn't understand the request.\n";
		} else if (StatusCode == 403) {
			return "You don't have permission to access /" + RelativePath + " on this server.\n";
		} else if (StatusCode == 404) {
			return "The requested URL /" + RelativePath + " was not found on this server.\n";
		} else {
			return "";
		}
	}

	public static HttpResponse GetResponse(Path RootPath, HttpRequest Request, Boolean bVerbose) {
		// Generate response base on request.
		HttpResponse Response = null;

		// Validate Request.
		if (Request.GetError().isEmpty()) {
			if (bVerbose) {
				System.out.println(Request.toString(true).get());
			}
		} else {
			Response = new HttpResponse(400, GenerateErrorHtml(RootPath, Paths.get("").toAbsolutePath(), 400),
					TEXT_HTML_FILE_TYPE);
			return Response;
		}

		// Validate path.
		Path FullPath = RootPath.resolve(Request.GetPath()).normalize();
		if (!FullPath.startsWith(RootPath)) {
			FullPath = RootPath;
		}

		switch (Request.GetOperation()) {
		case get:
			if (!Files.exists(FullPath)) {
				Response = new HttpResponse(404, GenerateErrorHtml(RootPath, FullPath, 404), TEXT_HTML_FILE_TYPE);
			} else if (!Files.isReadable(FullPath)) {
				Response = new HttpResponse(403, GenerateErrorHtml(RootPath, FullPath, 403), TEXT_HTML_FILE_TYPE);
			} else if (Files.isRegularFile(FullPath)) {
				try {
					final String FileContent = new String(Files.readAllBytes(FullPath));
					final String FileType = Files.probeContentType(FullPath);
					if (FileType != null) {
						Response = new HttpResponse(200, FileContent, FileType);
					} else {
						Response = new HttpResponse(200, FileContent);
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			} else if (Files.isDirectory(FullPath)) {
				Response = new HttpResponse(200, GenerateDirectoryHtml(RootPath, FullPath), TEXT_HTML_FILE_TYPE);
			} else {
				assert (false);
			}

			break;
		case post:
			if (!Files.exists(FullPath) || (Files.isRegularFile(FullPath) && Files.isWritable(FullPath))) {
				try {
					if (!Files.exists(FullPath.getParent())) {
						Files.createDirectories(FullPath.getParent());
					}
					Files.write(FullPath, Request.GetBody().getBytes());
					Response = new HttpResponse(201, GeneratePostHtml(RootPath, FullPath), TEXT_HTML_FILE_TYPE);
				} catch (IOException e) {
					Response = new HttpResponse(403, GenerateErrorHtml(RootPath, FullPath, 403), TEXT_HTML_FILE_TYPE);
				}
			} else {
				Response = new HttpResponse(403, GenerateErrorHtml(RootPath, FullPath, 403), TEXT_HTML_FILE_TYPE);
			}
			break;
		}

		return Response;
	}

}
