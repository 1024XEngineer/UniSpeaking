package com.unispeaking.common.exception.document;

/**
 * 通用文档文本提取的稳定业务错误码。
 */
public enum DocumentErrorCode {

	INPUT_REQUIRED(
			"DOCUMENT_INPUT_REQUIRED",
			"Document input is required"),
	TOO_LARGE(
			"DOCUMENT_TOO_LARGE",
			"Document exceeds the supported size limit"),
	FORMAT_UNSUPPORTED(
			"DOCUMENT_FORMAT_UNSUPPORTED",
			"Only text PDF and DOCX documents are supported"),
	CONTENT_INVALID(
			"DOCUMENT_CONTENT_INVALID",
			"Document content is invalid"),
	PDF_PAGE_LIMIT_EXCEEDED(
			"DOCUMENT_PDF_PAGE_LIMIT_EXCEEDED",
			"PDF exceeds the supported page limit"),
	TEXT_EMPTY(
			"DOCUMENT_TEXT_EMPTY",
			"Document contains no extractable text"),
	TEXT_TOO_LARGE(
			"DOCUMENT_TEXT_TOO_LARGE",
			"Extracted document text exceeds the supported length limit");

	private final String code;
	private final String defaultMessage;

	DocumentErrorCode(String code, String defaultMessage) {
		this.code = code;
		this.defaultMessage = defaultMessage;
	}

	public String code() {
		return code;
	}

	public String defaultMessage() {
		return defaultMessage;
	}
}
