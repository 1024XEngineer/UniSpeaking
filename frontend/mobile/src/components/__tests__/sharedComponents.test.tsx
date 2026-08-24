import { fireEvent, render } from '@testing-library/react-native';
import { Text } from 'react-native';

import { DevicePreviewFrame } from '../DevicePreviewFrame';
import { LearningAssetsHeader } from '../LearningAssetsHeader';
import { SceneCategoryTag } from '../SceneCategoryTag';
import { LearningStageProvider, useLearningStage } from '@/navigation/learningStage';

describe('shared mobile components', () => {
  it('renders category labels for explicit and fallback categories', async () => {
    const view = await render(
      <>
        <SceneCategoryTag category="food" />
        <SceneCategoryTag category="workplace" subtle />
        <SceneCategoryTag />
      </>,
    );

    expect(view.getByText('餐饮')).toBeTruthy();
    expect(view.getByText('职场')).toBeTruthy();
    expect(view.getByText('其他')).toBeTruthy();
  });

  it('opens the learning asset module menu and calls the selected destination', async () => {
    const onIelts = jest.fn();
    const onInterview = jest.fn();
    const view = await render(
      <LearningAssetsHeader current="scenes" onIelts={onIelts} onInterview={onInterview} />,
    );

    await fireEvent.press(view.getByLabelText('切换学习资产模块'));
    expect(view.getByText('雅思学习资产')).toBeTruthy();
    expect(view.getByText('英文面试资产')).toBeTruthy();
    await fireEvent.press(view.getByLabelText('进入雅思学习资产'));
    expect(onIelts).toHaveBeenCalledTimes(1);
    expect(view.queryByText('雅思学习资产')).toBeNull();

    await fireEvent.press(view.getByLabelText('切换学习资产模块'));
    await fireEvent.press(view.getByLabelText('关闭学习资产菜单'));
    expect(onInterview).not.toHaveBeenCalled();
  });

  it('renders the preview frame around arbitrary screen content', async () => {
    const view = await render(
      <DevicePreviewFrame>
        <Text>preview content</Text>
      </DevicePreviewFrame>,
    );

    expect(view.getByTestId('device-preview-stage')).toBeTruthy();
    expect(view.getByTestId('phone-preview-frame')).toBeTruthy();
    expect(view.getByTestId('mobile-preview-frame')).toBeTruthy();
    expect(view.getByText('preview content')).toBeTruthy();
  });

  it('passes learning stage state through the provider', async () => {
    const setImmersiveLearning = jest.fn();
    function Consumer() {
      const stage = useLearningStage();
      return <Text onPress={() => stage.setImmersiveLearning(true)}>{String(stage.immersiveLearning)}</Text>;
    }

    const view = await render(
      <LearningStageProvider immersiveLearning={false} setImmersiveLearning={setImmersiveLearning}>
        <Consumer />
      </LearningStageProvider>,
    );

    expect(view.getByText('false')).toBeTruthy();
    await fireEvent.press(view.getByText('false'));
    expect(setImmersiveLearning).toHaveBeenCalledWith(true);
  });
});
