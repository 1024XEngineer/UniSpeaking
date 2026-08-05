# Design QA — Mobile scene marketplace

**Comparison target**

- Web source: `/var/folders/7k/xsqc9hw542jg872bw3h8km480000gn/T/codex-clipboard-2d83f483-fdce-45a8-957e-30da181e572d.png`
- Previous mobile source: `/var/folders/7k/xsqc9hw542jg872bw3h8km480000gn/T/codex-clipboard-a8caea19-24a1-47ba-bd69-2ed1c4fa2ccd.png`
- Removed mobile confirmation source: `/var/folders/7k/xsqc9hw542jg872bw3h8km480000gn/T/codex-clipboard-9b732068-a20f-474c-940a-2ffe5fe6cb64.png`
- Web modal source: `/var/folders/7k/xsqc9hw542jg872bw3h8km480000gn/T/codex-clipboard-8d63f4ba-3eac-4633-93f1-82b3d8058333.png`
- Final marketplace implementation: `/Users/hanson/Library/Mobile Documents/com~apple~CloudDocs/七牛云/英语口语陪练/milestone2/Week3/Wed./UniSpeaking/frontend/unispeaking-native/scenes-larger-type-final.png`
- Final confirmation modal: `/Users/hanson/Library/Mobile Documents/com~apple~CloudDocs/七牛云/英语口语陪练/milestone2/Week3/Wed./UniSpeaking/frontend/unispeaking-native/scenes-preview-modal-final.png`
- Specialty menu state: `/Users/hanson/Library/Mobile Documents/com~apple~CloudDocs/七牛云/英语口语陪练/milestone2/Week3/Wed./UniSpeaking/frontend/unispeaking-native/scenes-specialty-menu-final.png`
- Browser capture contains the 450 × 900 logical mobile frame scaled into the Codex browser viewport.

**Full-view comparison evidence**

The Web source and final mobile capture were opened together in one comparison input. The mobile screen preserves the Web information hierarchy: page title, large custom-scene builder, discreet specialty entry, and three recommended scenarios. The desktop horizontal card row becomes a compact vertical list to fit the fixed mobile viewport.

**Focused region comparison evidence**

- Custom builder: `CREATE YOUR OWN`, title, explanatory copy, one uninterrupted 200-character input, count, and primary generation action are preserved. The quick-start/“场景灵感” surface has been removed.
- Specialty training: IELTS and English interview are removed from the main hierarchy and exposed through a small pill trigger with a two-option overlay menu.
- Daily recommendations: all three source cards retain numbering, category, task goal, duration, and a direct action affordance. The redundant “直接开始练习” label is removed and the section is given more separation from the builder.
- Scene confirmation: the full-page mobile intro has been removed from the entry flow. Recommendations and generated prompts now open an in-place modal that reproduces the Web hierarchy, summary rows, close action, return action, and confirmation action.
- Fixed layout: the page uses a non-scrolling viewport. Browser inspection found overflow hidden rather than a scrollable content surface.

**Required fidelity surfaces**

- Typography: strong black Chinese headings, small tracked English eyebrows, and restrained gray supporting text follow the Web source.
- Spacing and layout rhythm: compact mobile spacing keeps custom creation dominant while fitting all three recommendations above the persistent tab bar.
- Colors and visual tokens: white canvas, warm `#F8F8F5` builder, neutral borders, black action, and soft-gray pills match the existing Web/mobile design system.
- Icons: existing platform icon components are reused for arrows, specialty categories, and navigation; no improvised visual assets were added.
- Copy: the Web custom-scene guidance, daily recommendations, specialty labels, and confirmation summary are adapted without changing their intent.

**Primary interactions tested**

- The generate action is disabled when empty and enabled after input.
- The specialty pill opens a compact menu containing IELTS and English interview flows.
- A daily recommendation opens the Web-style confirmation modal without leaving the marketplace.
- “返回修改” and the close action dismiss the modal; “确认进入” opens training directly; the training back action returns to the marketplace.
- TypeScript validation and whitespace checks complete with zero errors.

**Comparison history**

- Iteration 1 — [P1] The previous black recommendation hero did not represent the Web custom-scene experience, while specialty cards dominated the screen and pushed recommendations below the fold. Fix: replaced the hero with the Web custom builder, moved specialty flows behind a small menu, and rebuilt recommendations as compact rows.
- Iteration 2 — [P2] A direct desktop-scale port would require vertical scrolling on mobile. Fix: condensed the builder footer and quick prompts, then converted the recommendation grid to three 65 px rows so the complete experience remains fixed in one viewport.
- Iteration 3 — the combined Web/mobile comparison found the hierarchy, color language, interaction priority, and fixed viewport behavior aligned with the request; no actionable P0/P1/P2 mismatch remains.
- Iteration 4 — [P1] The first mobile adaptation copied all four Web quick-start chips into a cramped 2 × 2 grid, making the builder feel mechanically scaled and visually fragmented. Fix: replaced the chips with one grouped, full-width suggestion surface, two readable rows at a time, a lightweight “换一组” control, and explicit selected feedback.
- Iteration 5 — the updated combined Web/mobile comparison confirms the Web structure is retained while the quick-start interaction is now adapted to mobile density; no actionable P0/P1/P2 mismatch remains.
- Iteration 6 — [P2] The mobile page title sat too close to the top safe-area boundary. Fix: moved the complete content stack down by 14 px so the heading gains breathing room without breaking the builder/recommendation rhythm or moving the fixed tab bar.
- Iteration 7 — [P1] The scene page still lacked the English eyebrow and did not share the exact title-header structure used by Assets and Profile. Fix: reused the shared `PageHeader` with `SCENARIO MARKETPLACE`, restoring the same safe-area offset, eyebrow/title/subtitle spacing, and typography. The builder and recommendation rows were then tightened only in their excess vertical space so all content remains visible above the fixed tab bar.
- Iteration 8 — [P1] The mobile “场景灵感” group remained unnecessary visual chrome, while the standalone scenario-intro route duplicated confirmation information and broke the Web interaction model. Fix: removed the inspiration group, enlarged the single prompt field, moved Daily Picks down, removed its helper label, and replaced the intro route with the Web-style confirmation modal.
- Iteration 9 — the Web modal source, final marketplace, and final mobile modal were inspected together. Summary hierarchy, row divisions, actions, backdrop, and direct-to-training behavior pass without remaining actionable P0/P1/P2 issues.
- Iteration 10 — [P1] The compact type scale reduced readability, the generation CTA visually collided with the input edge, and excessive empty space remained above the tab bar. Fix: raised the builder, input, CTA, Daily Picks, and recommendation-card type scales; gave the CTA a dedicated 14 px footer gap; enlarged the input and all three cards to redistribute the unused lower space. Before/after captures confirm the overlap is removed and the fixed viewport is more evenly filled.

**Follow-up polish**

- P3: connect the custom prompt to a generated scenario payload once the production scene-generation API is wired; the current visual prototype routes into the existing scenario training scaffold.

final result: passed

---

# Design QA — Onboarding CTA alignment

**Comparison target**

- Teacher source visual truth: `/var/folders/7k/xsqc9hw542jg872bw3h8km480000gn/T/codex-clipboard-ae0be413-3838-4dfe-917e-4dd1856fdcf9.png` (1168 × 1562 px).
- Level source visual truth: `/var/folders/7k/xsqc9hw542jg872bw3h8km480000gn/T/codex-clipboard-3ea60cf7-6391-431c-8a94-a9fa93dc99fc.png` (1130 × 1574 px).
- Teacher implementation screenshot: `/private/tmp/unispeaking-teacher-button-final2.png` (1280 × 720 px browser viewport).
- Level implementation screenshot: `/private/tmp/unispeaking-level-button-passed.png` (1280 × 720 px browser viewport).
- Combined teacher comparison: `/private/tmp/unispeaking-teacher-comparison-final.png`.
- Combined level comparison: `/private/tmp/unispeaking-level-comparison-final.png`.
- State: onboarding level and teacher selection, selected option/teacher visible.
- Density normalization: the two source captures and implementation captures were each scaled to 720 px height before side-by-side comparison. The app-owned phone viewport remained at its existing responsive stage scale.

**Full-view comparison evidence**

The source and final implementation were opened together for both onboarding screens. The circular CTA now occupies the same vertical position on Level and Teacher. On Teacher it is visibly closer to the avatar dial, while on Level it remains clearly separated from the selected card.

**Focused region comparison evidence**

The lower third was checked specifically because it contains the requested change. The teacher dial remains stationary while only the shared CTA footer moves upward by 32 px. The Level option stack uses a fixed 408 px region so the raised CTA does not overlap the final card.

**Required fidelity surfaces**

- Fonts and typography: unchanged from the existing onboarding design.
- Spacing and layout rhythm: CTA baseline is shared across both routes; teacher dial/CTA gap is reduced; Level card/CTA separation remains visible.
- Colors and visual tokens: existing paper fill, neutral border, black arrow, radius, and shadow are preserved.
- Image quality and asset fidelity: all teacher assets and crops are unchanged.
- Copy and content: no labels or onboarding copy changed.

**Primary interactions tested**

- Registered through the local preview to enter onboarding.
- Confirmed Level selection controls remain interactive and “下一步” opens Teacher.
- Confirmed Teacher selection controls and completion CTA remain exposed.
- Browser console checked with zero errors.
- TypeScript validation passed.

**Comparison history**

- Iteration 1 — [P2] Increasing the footer height moved both the dial and CTA upward together, leaving their gap effectively unchanged. Fix: restored the original footer footprint and translated only the shared CTA footer upward by 32 px.
- Iteration 2 — [P2] The raised Level CTA initially overlapped the selected card because the option stack expanded into the available space. Fix: constrained the option region to 408 px while retaining the original 90 px minimum card size.
- Iteration 3 — final paired comparisons show aligned CTA baselines, improved dial integration, no overlap, no clipping, and no actionable P0/P1/P2 mismatch.

**Follow-up polish**

- None required for this scoped alignment change.

final result: passed

---

# Design QA — Mobile scene training: 学 / 读 / 说

**Comparison target**

- Web implementation source: `milestone2/UniSpeaking/frontend/Unispeaking_fronted/src/App.jsx` (`Training` component)
- Web style source: `milestone2/UniSpeaking/frontend/Unispeaking_fronted/src/styles.css` (`stepper`, `training-workspace`, `learn-stage`, `read-stage`)
- Previous mobile source: `/var/folders/7k/xsqc9hw542jg872bw3h8km480000gn/T/codex-clipboard-38220ea2-7d2b-4188-a8f8-ce3dd9f04699.png`
- Final Learn state: `training-learn-final.png`
- Final Read state: `training-read-final.png`
- Final Speak state: `training-speak-final.png`

**Structural fidelity**

- The former three-prompt sequence is removed. The top-level journey now uses the Web hierarchy: `学 → 读 → 说`.
- Learn reuses Web concepts of a language-item index, current item, completion checks, pronunciation action, Chinese meaning, previous action, and forward action. Mobile condenses the desktop sidebar into a three-item horizontal strip.
- Read reuses the Web sentence card, Chinese meaning, rhythm guidance, standard demo, microphone action, score state, pass gate, and “进入模拟” transition.
- Speak reuses the Web progression into real scene conversation, selected teacher, AI role, opening prompt, translation, microphone response, and completion gate.
- Completed stages remain unlocked and can be revisited, matching the Web stepper behavior.

**Required fidelity surfaces**

- Typography: tracked English stage labels, large black learning content, and restrained gray instructional copy use the established dual-end visual language.
- Layout: all three states fit the fixed mobile viewport without vertical scrolling or content collision.
- Components: the existing teacher asset, icon system, color tokens, learning content, recommendation content, pill buttons, progress checks, and border language are reused.
- Interaction: Learn advances only through its language items; Read requires a completed recording state before simulation; Speak requires an active response before completion.

**Verification evidence**

- Browser walkthrough completed from Learn item 1 through Learn item 3, Read recording, score/pass state, and Speak scene.
- Browser walkthrough then returned from Speak to Learn and confirmed Read/Speak remain available, resolving an initial state-regression defect.
- TypeScript validation completed with zero errors.
- Visual inspection of all three final captures found no overflow, clipping, overlapping controls, or inconsistent button scale.

**Comparison history**

- Iteration 1 — [P0] Mobile treated three sentences as the product’s three learning steps. Fix: rebuilt the state model around Learn, Read, and Speak.
- Iteration 2 — [P1] A direct desktop sidebar would consume too much mobile width. Fix: converted it to a compact horizontal learning-item strip while retaining index, active, and completed states.
- Iteration 3 — [P1] Returning from Speak to Learn initially relocked later stages because availability was derived from the current stage. Fix: separated `unlockedStage` from `stage`, preserving Web-style review navigation.
- Iteration 4 — final Learn, Read, and Speak captures were inspected together. Information hierarchy, fixed-viewport fit, interaction gates, and cross-end styling pass without remaining actionable P0/P1/P2 issues.
- Iteration 5 — [P1] The Learn screen exposed all three language items above the active card, duplicating information and weakening the single-item learning focus. Fix: removed the horizontal item strip, renamed the section to “场景单词/场景词组”, and moved the retained `1 / 3` counter into the active language card. The supplied crop and final browser capture were inspected together; hierarchy and spacing now match the requested single-card structure.
- Iteration 6 — [P1] The circular back control competed with the persistent learning navigation and did not communicate cancellation. Fix: kept the scenario title at the upper left and replaced the back control with a compact “取消” action at the upper right, wired to the existing exit route.
- Iteration 7 — [P1] Words and phrases were still counted as one mixed sequence, while the pronunciation action used a labeled pill instead of the Web pattern. Fix: separated the Learn stage into independent Word and Phrase groups (`1 / 1` then `1 / 2` with reversible navigation), added phonetic data, and replaced the pill with a gray phonetic line plus standalone speaker icon. The Web pronunciation reference and final browser capture were inspected together; no actionable P0/P1/P2 mismatch remains.
- Iteration 8 — [P1] English section eyebrows remained above Word/Phrase/Read headings, the top safe-area spacing was compressed, and the textual cancel pill did not match the requested dismiss pattern. Fix: removed all inner-stage English eyebrows, increased the content top inset, and replaced the cancel pill with the shared close icon in a soft circular control.
- Iteration 9 — [P1] The first Read adaptation used a custom rhythm strip and combined demo/record controls, diverging from the Web learning component. Fix: rebuilt Read around the Web hierarchy—large sentence, Chinese meaning, microphone toggle, status and submission hint, standard-demo action, score state, and gated next action. The desktop Web reference and mobile capture were inspected together and preserve the same reading task hierarchy at mobile scale.
- Iteration 10 — [P1] Read feedback previously appeared only as an inline score badge. Fix: added the Web-style blocking assessment modal with large score, explanatory copy, bordered per-word result, green/red word feedback, and the “知道了” dismissal action. The Web modal and final mobile modal were inspected together; hierarchy and feedback semantics match.
- Iteration 11 — [P0] Speak still used a bespoke training card rather than the product’s established live conversation experience. Fix: extracted the production `CallExperience` from Free Conversation and reused it directly in Speak. Scenario mode forces subtitles on, keeps the waveform, timer, speaker transcript and optional translation, removes the subtitle toggle, and exposes only microphone and hang-up controls. The Web subtitle-mode reference and final mobile capture were inspected together; no actionable P0/P1/P2 mismatch remains.

**Follow-up polish**

- P3: replace the local simulated recording score and conversation completion with the production pronunciation-evaluation and generated-scene session APIs when those mobile endpoints are connected.

final result: passed
