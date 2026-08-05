import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const apiKey = process.env.DASHSCOPE_API_KEY?.trim();
if (!apiKey) {
  throw new Error("DASHSCOPE_API_KEY is required");
}

const endpoint = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation";
const model = process.env.QWEN_TTS_MODEL?.trim() || "qwen3-tts-flash";
const previews = [
  ["clara", "Katerina", "Hi, I’m Clara. Take your time — we’ll make speaking English feel natural and easy."],
  ["james", "Ryan", "Hello, I’m James. Let’s turn your ideas into clear, confident English."],
  ["leo", "Ethan", "Hey, I’m Leo! Don’t overthink it — just speak, and we’ll have a great chat."],
  ["david", "Aiden", "Hi, I’m David. We’ll make your English concise, natural, and ready for work."],
  ["emily", "Serena", "Hi, I’m Emily. Let’s talk about everyday life and enjoy the conversation."],
  ["arthur", "Eldric Sage", "Good to meet you. I’m Arthur. Let’s give your English more depth and confidence."],
];

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
const outputDirectory = path.resolve(scriptDirectory, "../public/teachers/audio");
await mkdir(outputDirectory, { recursive: true });

for (const [id, voice, text] of previews) {
  const response = await fetch(endpoint, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${apiKey}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      model,
      input: {
        text,
        voice,
        language_type: "English",
      },
    }),
  });
  if (!response.ok) {
    throw new Error(`${id} preview generation failed with HTTP ${response.status}`);
  }
  const result = await response.json();
  const audioUrl = result?.output?.audio?.url;
  if (!audioUrl) {
    throw new Error(`${id} preview generation did not return an audio URL`);
  }
  const audioResponse = await fetch(audioUrl);
  if (!audioResponse.ok) {
    throw new Error(`${id} preview download failed with HTTP ${audioResponse.status}`);
  }
  const audio = Buffer.from(await audioResponse.arrayBuffer());
  if (audio.length < 12 || audio.toString("ascii", 0, 4) !== "RIFF" || audio.toString("ascii", 8, 12) !== "WAVE") {
    throw new Error(`${id} preview is not a WAV file`);
  }
  await writeFile(path.join(outputDirectory, `${id}.wav`), audio);
}
