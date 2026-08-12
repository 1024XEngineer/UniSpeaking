import { fireEvent, render, waitFor } from '@testing-library/react-native';

import type { SceneLearningRecord } from '@/data/learningAssets';

import { AssetsScreen, SceneAssetDetail, SceneAssetDetailLoader } from '../AssetsScreen';

const summaryRecord: SceneLearningRecord = {
  id: 'scene/airport',
  title: '机场行李托运',
  date: '2026-08-05',
  status: '已完成',
  score: 88.6,
  practiceCount: 2,
  expressions: [],
  conversation: [],
};

const detailRecord: SceneLearningRecord = {
  ...summaryRecord,
  expressions: [
    {
      id: 'word-1',
      type: '单词',
      englishText: 'baggage',
      chineseText: '行李',
    },
  ],
  conversation: [],
};

describe('AssetsScreen backend binding', () => {
  it('renders backend records instead of seeded local demo records', async () => {
    const onOpenRecord = jest.fn();
    let resolveRecords: (records: SceneLearningRecord[]) => void = () => undefined;
    const recordsPromise = new Promise<SceneLearningRecord[]>((resolve) => {
      resolveRecords = resolve;
    });
    const service = {
      listRecords: jest.fn(() => recordsPromise),
      getRecord: jest.fn(async () => detailRecord),
    };
    const screen = await render(
      <AssetsScreen
        assetService={service}
        onOpenRecord={onOpenRecord}
        onOpenIelts={jest.fn()}
      />,
    );

    expect(screen.getByText('正在同步场景学习资产…')).toBeTruthy();
    resolveRecords([summaryRecord]);
    await waitFor(() => expect(screen.getByText('机场行李托运')).toBeTruthy());
    expect(screen.getByText('89 分')).toBeTruthy();
    expect(screen.queryByText('咖啡店点单')).toBeNull();
    await fireEvent.press(screen.getByText('机场行李托运'));
    expect(onOpenRecord).toHaveBeenCalledWith(summaryRecord);
  });

  it('shows a recoverable backend list failure', async () => {
    const service = {
      listRecords: jest.fn(async () => {
        throw new Error('资产服务暂时不可用');
      }),
      getRecord: jest.fn(async () => detailRecord),
    };
    const screen = await render(
      <AssetsScreen
        assetService={service}
        onOpenRecord={jest.fn()}
        onOpenIelts={jest.fn()}
      />,
    );

    await waitFor(() =>
      expect(screen.getByText('资产服务暂时不可用')).toBeTruthy(),
    );
    expect(screen.getByText('重新加载')).toBeTruthy();
  });
});

describe('SceneAssetDetailLoader', () => {
  it('loads the backend detail by scene id before showing expressions', async () => {
    const service = {
      listRecords: jest.fn(async () => []),
      getRecord: jest.fn(async () => detailRecord),
    };
    const screen = await render(
      <SceneAssetDetailLoader
        assetService={service}
        sceneId="scene/airport"
        onBack={jest.fn()}
        onPractice={jest.fn()}
        onDelete={jest.fn()}
      />,
    );

    await waitFor(() => expect(screen.getByText('baggage')).toBeTruthy());
    expect(service.getRecord).toHaveBeenCalledWith('scene/airport');
    expect(screen.getByText('行李')).toBeTruthy();
  });

  it('plays and stops a saved expression through backend TTS', async () => {
    const ttsPlayer = { play: jest.fn(async () => undefined), stop: jest.fn() };
    const screen = await render(
      <SceneAssetDetail
        record={detailRecord}
        onBack={jest.fn()}
        onPractice={jest.fn()}
        onDelete={jest.fn()}
        ttsPlayer={ttsPlayer}
      />,
    );

    await fireEvent.press(screen.getByLabelText('播放 baggage'));
    expect(ttsPlayer.play).toHaveBeenCalledWith(detailRecord.id, 'baggage');
    await fireEvent.press(screen.getByLabelText('停止播放 baggage'));
    expect(ttsPlayer.stop).toHaveBeenCalled();
  });
});
