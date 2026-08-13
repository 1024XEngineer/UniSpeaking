import type { IeltsGeneration, IeltsTraining } from '../types';
import { resolvePart2CueCard } from '../part2CueCard';

function generatedPart2(question: string, cuePoints: string[]): IeltsGeneration {
  return {
    ieltsId: 'ielts-mock-1',
    mode: 'MOCK_TEST',
    selectedPart: null,
    selectedTopicId: null,
    title: 'IELTS Mock Test',
    content: {
      part1: [],
      part2: [{ question, cue_points: cuePoints, recommended_expressions: [] }],
      part3: [],
    },
    voiceId: 'Harvey',
    scenePrompt: 'prompt',
  };
}

describe('resolvePart2CueCard', () => {
  it('uses the question randomly selected by the backend for a mock test', () => {
    const training = {
      topicId: 'old-topic',
      title: 'Old preview',
      part: 'PART_2',
      questions: [{
        id: 'old-question',
        part: 'PART_2',
        sortNo: 1,
        questionText: 'Old preview question',
        cuePoints: ['Old point'],
        recommendedExpressions: [],
      }],
    } satisfies IeltsTraining;

    expect(resolvePart2CueCard(
      generatedPart2('Describe a real selected topic.', ['What it is', 'Why it matters']),
      training,
    )).toEqual({
      title: 'Describe a real selected topic.',
      points: ['What it is', 'Why it matters'],
    });
  });

  it('does not invent mock cue-card content when the backend returns no question', () => {
    expect(resolvePart2CueCard(generatedPart2('', []), null)).toEqual({
      title: 'Part 2 题卡暂不可用',
      points: [],
    });
  });
});
