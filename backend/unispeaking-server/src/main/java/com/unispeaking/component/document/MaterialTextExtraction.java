package com.unispeaking.component.document;

import com.unispeaking.common.document.DocumentTextExtractor;
import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.common.exception.InterviewErrorCode;
import com.unispeaking.common.exception.document.DocumentErrorCode;
import com.unispeaking.common.exception.ocr.OcrErrorCode;
import com.unispeaking.common.exception.ocr.OcrException;
import com.unispeaking.domain.dto.ocr.OcrImage;
import com.unispeaking.domain.dto.scene.InterviewMaterialPreparationInput;
import com.unispeaking.domain.dto.scene.InterviewResumeFile;
import com.unispeaking.provider.OcrProvider;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 面试材料文本抽取组合器：把原始 JD/简历解析成纯文本，供后续一次性脱敏与 LLM-1 整理。
 * <p>规则（issue #61 + N3）：
 * <ul>
 *   <li>JD = 文本 XOR 单张图片（图片走 {@link OcrProvider#recognizeText}，前置
 *       {@code OcrProvider.available()} 门控，不可用抛 {@code OCR_UNAVAILABLE}）。</li>
 *   <li>简历 = 文本 XOR PDF/DOCX 文件（{@link DocumentTextExtractor} 抽取）；{@code .doc}
 *       与图片简历明确抛 {@code DOCUMENT_FORMAT_UNSUPPORTED}；扫描版 PDF 由
 *       {@link DocumentTextExtractor} 以 {@code DOCUMENT_TEXT_EMPTY} 拒绝。</li>
 * </ul>
 * 本组件只做抽取，不做脱敏（脱敏在 Service 层调用 {@link MaterialDesensitizer} 一次性完成）。
 */
@Component
public class MaterialTextExtraction {

	/** 与 {@link DocumentTextExtractor#MAX_TEXT_CHARS} 对齐的粘贴文本字符上限。 */
	private static final int MAX_TEXT_CHARS = 20_000;

	private final OcrProvider ocrProvider;
	private final DocumentTextExtractor documentTextExtractor;

	public MaterialTextExtraction(
			OcrProvider ocrProvider,
			DocumentTextExtractor documentTextExtractor) {
		this.ocrProvider = Objects.requireNonNull(
				ocrProvider, "ocrProvider must not be null");
		this.documentTextExtractor = Objects.requireNonNull(
				documentTextExtractor, "documentTextExtractor must not be null");
	}

	/**
	 * 抽取 JD 文本 + 简历文本；简历缺省时 {@code resumeText} 为 {@code null} 且
	 * {@code resumeAbsent} 为 {@code true}。
	 */
	public MaterialTextResult extract(InterviewMaterialPreparationInput input) {
		if (input == null) {
			throw inputInvalid("材料输入不能为空");
		}
		String jobDescriptionText = extractJobDescription(input);
		ResumeText resume = extractResume(input);
		return new MaterialTextResult(
				jobDescriptionText,
				resume.text(),
				resume.absent());
	}

	private String extractJobDescription(InterviewMaterialPreparationInput input) {
		String text = input.jobDescriptionText();
		OcrImage image = input.jobDescriptionImage();
		boolean hasText = text != null;
		boolean hasImage = image != null
				&& image.content() != null
				&& image.content().length > 0;
		if (hasText && hasImage) {
			throw inputInvalid("JD 文本与图片只能二选一");
		}
		if (hasImage) {
			if (!ocrProvider.available()) {
				throw new OcrException(OcrErrorCode.UNAVAILABLE);
			}
			String recognized = ocrProvider.recognizeText(List.of(image));
			if (recognized == null || recognized.isBlank()) {
				throw inputInvalid("JD 图片未能识别出文字");
			}
			return recognized;
		}
		if (hasText) {
			return requireTextLength(text, "JD 文本");
		}
		throw inputInvalid("JD 必须提供文本或单张图片");
	}

	private ResumeText extractResume(InterviewMaterialPreparationInput input) {
		String text = input.resumeText();
		InterviewResumeFile file = input.resumeFile();
		boolean hasText = text != null;
		boolean hasFile = file != null
				&& file.content() != null
				&& file.content().length > 0;
		if (hasText && hasFile) {
			throw inputInvalid("简历文本与文件只能二选一");
		}
		if (hasText) {
			return new ResumeText(requireTextLength(text, "简历文本"), false);
		}
		if (!hasFile) {
			return new ResumeText(null, true);
		}
		rejectUnsupportedResumeFile(file);
		String extracted = documentTextExtractor.extractText(
				file.filename(),
				file.mimeType(),
				file.content());
		return new ResumeText(extracted, false);
	}

	private static void rejectUnsupportedResumeFile(InterviewResumeFile file) {
		if (isLegacyDoc(file)) {
			throw new BusinessException(
					DocumentErrorCode.FORMAT_UNSUPPORTED.code(),
					"请上传 PDF/DOCX 文本简历，不支持 .doc");
		}
		if (isImage(file)) {
			throw new BusinessException(
					DocumentErrorCode.FORMAT_UNSUPPORTED.code(),
					"图片简历暂不支持，请上传 PDF/DOCX 文本简历");
		}
	}

	private static boolean isLegacyDoc(InterviewResumeFile file) {
		String name = normalizedFilename(file);
		return name.endsWith(".doc") && !name.endsWith(".docx");
	}

	private static boolean isImage(InterviewResumeFile file) {
		String mime = normalizedMimeType(file.mimeType());
		if (mime.startsWith("image/")) {
			return true;
		}
		String name = normalizedFilename(file);
		return name.endsWith(".png")
				|| name.endsWith(".jpg")
				|| name.endsWith(".jpeg")
				|| name.endsWith(".gif")
				|| name.endsWith(".bmp")
				|| name.endsWith(".webp");
	}

	private static String normalizedFilename(InterviewResumeFile file) {
		return file.filename() == null
				? ""
				: file.filename().trim().toLowerCase(Locale.ROOT);
	}

	private static String normalizedMimeType(String mimeType) {
		if (mimeType == null) {
			return "";
		}
		int parametersStart = mimeType.indexOf(';');
		String value = parametersStart >= 0
				? mimeType.substring(0, parametersStart)
				: mimeType;
		return value.trim().toLowerCase(Locale.ROOT);
	}

	private static BusinessException inputInvalid(String message) {
		return new BusinessException(
				InterviewErrorCode.INTERVIEW_REQUEST_INVALID,
				message);
	}

	private static String requireTextLength(String text, String label) {
		if (text.length() > MAX_TEXT_CHARS) {
			throw new BusinessException(
					InterviewErrorCode.INTERVIEW_REQUEST_INVALID,
					label + "不能超过 " + MAX_TEXT_CHARS + " 个字符");
		}
		return text;
	}

	/** 抽取结果：JD 文本 + 简历文本 + 空简历标记。 */
	public record MaterialTextResult(
			String jobDescriptionText,
			String resumeText,
			boolean resumeAbsent) {
	}

	private record ResumeText(String text, boolean absent) {
	}
}
