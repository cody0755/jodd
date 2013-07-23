// Copyright (c) 2003-2013, Jodd Team (jodd.org). All Rights Reserved.

package jodd.http;

import jodd.JoddHttp;
import jodd.datetime.TimeUtil;
import jodd.io.FastCharArrayWriter;
import jodd.io.FileNameUtil;
import jodd.io.FileUtil;
import jodd.io.StreamUtil;
import jodd.upload.FileUpload;
import jodd.upload.MultipartStreamParser;
import jodd.util.MimeTypes;
import jodd.util.RandomStringUtil;
import jodd.util.StringPool;
import jodd.util.StringUtil;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.LinkedHashMap;
import java.util.Map;

import static jodd.util.StringPool.CRLF;

/**
 * Base class for {@link HttpRequest} and {@link HttpResponse}.
 */
@SuppressWarnings("unchecked")
public abstract class HttpBase<T> {

	public static final String HEADER_ACCEPT_ENCODING = "Accept-Encoding";
	public static final String HEADER_CONTENT_TYPE = "Content-Type";
	public static final String HEADER_CONTENT_LENGTH = "Content-Length";
	public static final String HEADER_CONTENT_ENCODING = "Content-Encoding";
	public static final String HEADER_HOST = "Host";
	public static final String HEADER_ETAG = "ETag";

	protected String httpVersion = "HTTP/1.1";
	protected Map<String, String> headers = new LinkedHashMap<String, String>();

	protected HttpParamsMap form;	// holds form data (when used)
	protected String body;			// holds raw body string (always)

	// ---------------------------------------------------------------- properties

	/**
	 * Returns HTTP version string. By default it's "HTTP/1.1".
	 */
	public String httpVersion() {
		return httpVersion;
	}

	/**
	 * Sets the HTTP version string. Must be formed like "HTTP/1.1".
	 */
	public T httpVersion(String httpVersion) {
		this.httpVersion = httpVersion;
		return (T) this;
	}

	// ---------------------------------------------------------------- headers

	/**
	 * Returns value of header parameter.
	 */
	public String header(String name) {
		String key = name.trim().toLowerCase();

		return headers.get(key);
	}

	/**
	 * Removes header parameter.
	 */
	public void removeHeader(String name) {
		String key = name.trim().toLowerCase();

		headers.remove(key);
	}

	/**
	 * Sets header parameter. Existing parameter is overwritten.
	 * The order of header parameters is preserved.
	 * Also detects 'Content-Type' header and extracts
	 * {@link #mediaType() media type} and {@link #charset() charset}
	 * values.
	 */
	public T header(String name, String value) {
		String key = name.trim().toLowerCase();

		value = value.trim();

		if (key.equalsIgnoreCase(HEADER_CONTENT_TYPE)) {
			mediaType = HttpUtil.extractMediaType(value);
			charset = HttpUtil.extractContentTypeCharset(value);
		}

		headers.put(key, value);
		return (T) this;
	}

	/**
	 * Internal direct header setting.
	 */
	protected void _header(String name, String value) {
		String key = name.trim().toLowerCase();
		value = value.trim();
		headers.put(key, value);
	}

	/**
	 * Sets <code>int</code> value as header parameter,
	 * @see #header(String, String)
	 */
	public T header(String name, int value) {
		_header(name, String.valueOf(value));
		return (T) this;
	}

	/**
	 * Sets date value as header parameter.
	 * @see #header(String, String)
	 */
	public T header(String name, long millis) {
		_header(name, TimeUtil.formatHttpDate(millis));
		return (T) this;
	}

	// ---------------------------------------------------------------- content type

	protected String charset;

	/**
	 * Returns charset, as defined by 'Content-Type' header.
	 * If not set, returns <code>null</code> - indicating
	 * the default charset (ISO-8859-1).
	 */
	public String charset() {
		return charset;
	}

	/**
	 * Defines just content type charset. Setting this value to
	 * <code>null</code> will remove the charset information from
	 * the header.
	 */
	public T charset(String charset) {
		this.charset = null;
		contentType(null, charset);
		return (T) this;
	}


	protected String mediaType;

	/**
	 * Returns media type, as defined by 'Content-Type' header.
	 * If not set, returns <code>null</code> - indicating
	 * the default media type, depending on request/response.
	 */
	public String mediaType() {
		return mediaType;
	}

	/**
	 * Defines just content media type.
	 * Setting this value to <code>null</code> will
	 * not have any effects.
	 */
	public T mediaType(String mediaType) {
		contentType(mediaType, null);
		return (T) this;
	}

	/**
	 * Returns full "Content-Type" header.
	 * It consists of {@link #mediaType() media type}
	 * and {@link #charset() charset}.
	 */
	public String contentType() {
		return header(HEADER_CONTENT_TYPE);
	}

	/**
	 * Sets full "Content-Type" header. Both {@link #mediaType() media type}
	 * and {@link #charset() charset} are overridden.
	 */
	public T contentType(String contentType) {
		header(HEADER_CONTENT_TYPE, contentType);
		return (T) this;
	}

	/**
	 * Sets "Content-Type" header by defining media-type and/or charset parameter.
	 * This method may be used to update media-type and/or charset by passing
	 * non-<code>null</code> value for changes.
	 * <p>
	 * Important: if Content-Type header has some other parameters, they will be removed!
	 */
	public T contentType(String mediaType, String charset) {
		if (mediaType == null) {
			mediaType = this.mediaType;
		} else {
			this.mediaType = mediaType;
		}

		if (charset == null) {
			charset = this.charset;
		} else {
			this.charset = charset;
		}

		String contentType = mediaType;
		if (charset != null) {
			contentType += ";charset=" + charset;
		}

		_header(HEADER_CONTENT_TYPE, contentType);
		return (T) this;
	}

	// ---------------------------------------------------------------- common headers

	/**
	 * Returns full "Content-Length" header or
	 * <code>null</code> if not set.
	 */
	public String contentLength() {
		return header(HEADER_CONTENT_LENGTH);
	}

	/**
	 * Sets the full "Content-Length" header.
	 */
	public T contentLength(int value) {
		header(HEADER_CONTENT_LENGTH, value);
		return (T) this;
	}

	/**
	 * Returns "Content-Encoding" header.
	 */
	public String contentEncoding() {
		return header(HEADER_CONTENT_ENCODING);
	}

	/**
	 * Returns "Accept-Encoding" header.
	 */
	public String acceptEncoding() {
		return header(HEADER_ACCEPT_ENCODING);
	}

	/**
	 * Sets "Accept-Encoding" header.
	 */
	public T acceptEncoding(String encodings) {
		header(HEADER_ACCEPT_ENCODING, encodings);
		return (T) this;
	}

	// ---------------------------------------------------------------- form

	protected void initForm() {
		if (form == null) {
			form = new HttpParamsMap();
		}
	}

	/**
	 * Sets the form parameter.
	 */
	public T form(String name, Object value) {
		initForm();
		form.put(name, value);
		return (T) this;
	}

	/**
	 * Sets many form parameters at once.
	 */
	public T form(String name, Object value, Object... parameters) {
		initForm();

		form.put(name, value);
		for (int i = 0; i < parameters.length; i += 2) {
			name = parameters[i].toString();

			form.put(name, parameters[i + 1]);
		}
		return (T) this;
	}

	/**
	 * Sets many form parameters at once.
	 */
	public T form(Map<String, Object> formMap) {
		initForm();

		for (Map.Entry<String, Object> entry : formMap.entrySet()) {
			form.put(entry.getKey(), entry.getValue());
		}
		return (T) this;
	}

	/**
	 * Return map of form parameters.
	 */
	public Map<String, Object> form() {
		return form;
	}

	// ---------------------------------------------------------------- form encoding

	protected String formEncoding = JoddHttp.defaultFormEncoding;

	/**
	 * Defines encoding for forms parameters. Default value is
	 * copied from {@link JoddHttp#defaultFormEncoding}.
	 * It is overridden by {@link #charset() charset} value.
	 */
	public T formEncoding(String encoding) {
		this.formEncoding = encoding;
		return (T) this;
	}

	// ---------------------------------------------------------------- body

	/**
	 * Returns <b>raw</b> body as received or set (always in ISO-8859-1 encoding).
	 * If body content is a text, use {@link #bodyText()} to get it converted.
	 */
	public String body() {
		return body;
	}

	/**
	 * Returns <b>raw</b> body bytes.
	 */
	public byte[] bodyBytes() {
		if (body == null) {
			return null;
		}
		try {
			return body.getBytes(StringPool.ISO_8859_1);
		} catch (UnsupportedEncodingException ignore) {
			return null;
		}
	}

	/**
	 * Returns {@link #body() body content} as text. If {@link #charset() charset parameter}
	 * of "Content-Type" header is defined, body string charset is converted, otherwise
	 * the same raw body content is returned.
	 */
	public String bodyText() {
		if (charset != null) {
			return StringUtil.convertCharset(body, StringPool.ISO_8859_1, charset);
		}
		return body();
	}

	/**
	 * Sets <b>raw</b> body content and discards all form parameters.
	 * Important: body string is in RAW format, meaning, ISO-8859-1 encoding.
	 * Also sets "Content-Length" parameter. However, "Content-Type" is not set
	 * and it is expected from user to set this one.
	 */
	public T body(String body) {
		this.body = body;
		this.form = null;
		contentLength(body.length());
		return (T) this;
	}

	/**
	 * Defines body text and content type (as media type and charset).
	 * Body string will be converted to {@link #body(String) raw body string}
	 * and "Content-Type" header will be set.
	 */
	public T bodyText(String body, String mediaType, String charset) {
		body = StringUtil.convertCharset(body, charset, StringPool.ISO_8859_1);
		contentType(mediaType, charset);
		body(body);
		return (T) this;
	}

	/**
	 * Defines {@link #bodyText(String, String, String) body text content}
	 * that will be encoded in {@link JoddHttp#defaultBodyEncoding default body encoding}.
	 */
	public T bodyText(String body, String mediaType) {
		return bodyText(body, mediaType, JoddHttp.defaultBodyEncoding);
	}
	/**
	 * Defines {@link #bodyText(String, String, String) body text content}
	 * that will be encoded as {@link JoddHttp#defaultBodyMediaType default body media type}
	 * in {@link JoddHttp#defaultBodyEncoding default body encoding}.
	 */
	public T bodyText(String body) {
		return bodyText(body, JoddHttp.defaultBodyMediaType, JoddHttp.defaultBodyEncoding);
	}

	/**
	 * Sets <b>raw</b> body content and discards form parameters.
	 * Also sets "Content-Length" and "Content-Type" parameter.
	 * @see #body(String)
	 */
	public T body(byte[] content, String contentType) {
		String body = null;
		try {
			body = new String(content, StringPool.ISO_8859_1);
		} catch (UnsupportedEncodingException ignore) {
		}
		contentType(contentType);
		return body(body);
	}

	// ---------------------------------------------------------------- body form

	/**
	 * Returns <code>true</code> if form contains non-string elements (i.e. files).
	 */
	protected boolean isFormMultipart() {
		for (Object o : form.values()) {
			Class type = o.getClass();

			if (type.equals(String.class) || type.equals(String[].class)) {
				continue;
			}
			return true;
		}
	    return false;
	}

	/**
	 * Creates form string and sets few headers.
	 */
	protected String formString() {
		if (form == null || form.isEmpty()) {
			return StringPool.EMPTY;
		}

		// todo allow user to force usage of multipart

		if (!isFormMultipart()) {
			// determine form encoding
			String formEncoding = charset;

			if (formEncoding == null) {
				formEncoding = this.formEncoding;
			}

			// encode
			String formQueryString = HttpUtil.buildQuery(form, formEncoding);

			contentType("application/x-www-form-urlencoded", null);
			contentLength(formQueryString.length());

			return formQueryString;
		}

		String boundary = StringUtil.repeat('-', 10) + RandomStringUtil.randomAlphaNumeric(10);

		StringBuilder sb = new StringBuilder();

		for (Map.Entry<String, Object> entry : form.entrySet()) {

			sb.append("--");
			sb.append(boundary);
			sb.append(CRLF);

			String name = entry.getKey();
			Object value =  entry.getValue();
			Class type = value.getClass();

			if (type == String.class) {
				sb.append("Content-Disposition: form-data; name=\"").append(name).append('"').append(CRLF);
				sb.append(CRLF);
				sb.append(value);
			} else if (type == String[].class) {
				String[] array = (String[]) value;
				for (String v : array) {
					sb.append("Content-Disposition: form-data; name=\"").append(name).append('"').append(CRLF);
					sb.append(CRLF);
					sb.append(v);
				}
			} else if (type == File.class) {
				File file = (File) value;
				String fileName = FileNameUtil.getName(file.getName());

				sb.append("Content-Disposition: form-data; name=\"").append(name);
				sb.append("\"; filename=\"").append(fileName).append('"').append(CRLF);

				String mimeType = MimeTypes.getMimeType(FileNameUtil.getExtension(fileName));
				sb.append(HEADER_CONTENT_TYPE).append(": ").append(mimeType).append(CRLF);
				sb.append("Content-Transfer-Encoding: binary").append(CRLF);
				sb.append(CRLF);

				try {
					char[] chars = FileUtil.readChars(file, StringPool.ISO_8859_1);
					sb.append(chars);
				} catch (IOException ioex) {
					throw new HttpException(ioex);
				}
			} else {
				throw new HttpException("Unsupported parameter type: " + type.getName());
			}
			sb.append(CRLF);
		}

		sb.append("--").append(boundary).append("--");

		// the end
		contentType("multipart/form-data; boundary=" + boundary);
		contentLength(sb.length());

		return sb.toString();
	}

	// ---------------------------------------------------------------- send

	/**
	 * Returns byte array of request or response.
	 */
	public byte[] toByteArray() {
		try {
			return toString().getBytes(StringPool.ISO_8859_1);
		} catch (UnsupportedEncodingException ignore) {
			return null;
		}
	}

	/**
	 * Sends request or response to output stream.
	 */
	public void sendTo(OutputStream out) throws IOException {
		byte[] bytes = toByteArray();

		out.write(bytes);

		out.flush();
	}

	// ---------------------------------------------------------------- parsing

	/**
	 * Parses headers.
	 */
	protected void readHeaders(BufferedReader reader) {
		while (true) {
			String line;
			try {
				line = reader.readLine();
			} catch (IOException ioex) {
				throw new HttpException(ioex);
			}

			if (StringUtil.isBlank(line)) {
				break;
			}

			int ndx = line.indexOf(':');
			if (ndx != -1) {
				header(line.substring(0, ndx), line.substring(ndx + 1));
			} else {
				throw new HttpException("Invalid header: " + line);
			}
		}
	}

	/**
	 * Parses body.
	 */
	protected void readBody(BufferedReader reader) {
		String bodyString = null;

		// content length
		String contentLen = contentLength();
		if (contentLen != null) {
			int len = Integer.parseInt(contentLen);

			FastCharArrayWriter fastCharArrayWriter = new FastCharArrayWriter(len);

			try {
				StreamUtil.copy(reader, fastCharArrayWriter, len);
			} catch (IOException ioex) {
				throw new HttpException(ioex);
			}

			bodyString = fastCharArrayWriter.toString();
		}

		// chunked encoding
		String transferEncoding = header("Transfer-Encoding");
		if (transferEncoding != null && transferEncoding.equalsIgnoreCase("chunked")) {

			FastCharArrayWriter fastCharArrayWriter = new FastCharArrayWriter();
			try {
				while (true) {
					String line = reader.readLine();

					if (StringUtil.isBlank(line)) {
						break;
					}

					int len = Integer.parseInt(line, 16);

					if (len != 0) {
						StreamUtil.copy(reader, fastCharArrayWriter, len);
						reader.readLine();
					}
				}
			} catch (IOException ioex) {
				throw new HttpException(ioex);
			}

			bodyString = fastCharArrayWriter.toString();
		}

		// no body
		if (bodyString == null) {

			if (httpVersion().equals("HTTP/1.0")) {
				// in HTTP 1.0 body ends when stream closes
				FastCharArrayWriter fastCharArrayWriter = new FastCharArrayWriter();
				try {
					StreamUtil.copy(reader, fastCharArrayWriter);
				} catch (IOException ioex) {
					throw new HttpException(ioex);
				}
				bodyString = fastCharArrayWriter.toString();
			} else {
				body = null;
				return;
			}
		}

		// BODY READY - PARSE BODY
		String charset = this.charset;
		if (charset == null) {
			charset = StringPool.ISO_8859_1;
		}
		body = bodyString;

		String mediaType = mediaType();

		if (mediaType == null) {
			mediaType = StringPool.EMPTY;
		} else {
			mediaType = mediaType.toLowerCase();
		}

		if (mediaType.equals("application/x-www-form-urlencoded")) {
			form = HttpUtil.parseQuery(bodyString, true);
			return;
		}

		if (mediaType.equals("multipart/form-data")) {
			form = new HttpParamsMap();

			MultipartStreamParser multipartParser = new MultipartStreamParser();

			try {
				byte[] bodyBytes = bodyString.getBytes(StringPool.ISO_8859_1);
				ByteArrayInputStream bin = new ByteArrayInputStream(bodyBytes);
				multipartParser.parseRequestStream(bin, charset);
			} catch (IOException ioex) {
				throw new HttpException(ioex);
			}

			// string parameters
			for (String paramName : multipartParser.getParameterNames()) {
				String[] values = multipartParser.getParameterValues(paramName);
				if (values.length == 1) {
					form.put(paramName, values[0]);
				} else {
					form.put(paramName, values);
				}
			}

			// file parameters
			for (String paramName : multipartParser.getFileParameterNames()) {
				FileUpload[] values = multipartParser.getFiles(paramName);
				if (values.length == 1) {
					form.put(paramName, values[0]);
				} else {
					form.put(paramName, values);
				}
			}

			return;
		}

		// body is a simple content

		form = null;
	}

}