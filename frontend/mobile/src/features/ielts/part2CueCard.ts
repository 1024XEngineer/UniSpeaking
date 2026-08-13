import type { IeltsGeneration, IeltsTraining } from './types';

export type Part2CueCard = {
  title: string;
  points: string[];
};

export function resolvePart2CueCard(
  generated: IeltsGeneration,
  training: IeltsTraining | null,
): Part2CueCard {
  const generatedQuestion = generated.content.part2?.[0];
  const trainingQuestion = training?.questions[0];
  const generatedTitle = generatedQuestion?.question?.trim();
  const trainingTitle = trainingQuestion?.questionText?.trim();
  return {
    title: generatedTitle || trainingTitle || 'Part 2 题卡暂不可用',
    points: generatedQuestion?.cue_points?.length
      ? generatedQuestion.cue_points
      : trainingQuestion?.cuePoints ?? [],
  };
}
