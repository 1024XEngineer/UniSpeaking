import { fireEvent, render } from '@testing-library/react-native';

import { LevelSelector, SpeedSelector, TeacherSelector } from '../ConversationSettings';

describe('conversation setting selectors', () => {
  it('reports speed selection through its public callback', async () => {
    const changeSpeed = jest.fn();
    const speed = await render(<SpeedSelector value="自然" onChange={changeSpeed} />);
    await fireEvent.press(speed.getByText('慢一些'));
    expect(changeSpeed).toHaveBeenCalledWith('慢一些');
    speed.unmount();
  });

  it('reports level selection through its public callback', async () => {
    const changeLevel = jest.fn();
    const level = await render(<LevelSelector value="starter" onChange={changeLevel} />);
    await fireEvent.press(level.getByText('可以简单交流'));
    expect(changeLevel).toHaveBeenCalledWith('basic');
    level.unmount();
  });

  it('selects and previews the chosen teacher', async () => {
    const selectTeacher = jest.fn();
    const previewTeacher = jest.fn();
    const teacher = await render(
      <TeacherSelector selectedId="clara" onSelect={selectTeacher} onPreview={previewTeacher} />,
    );
    await fireEvent.press(teacher.getByLabelText('选择 James'));
    expect(selectTeacher).toHaveBeenCalledWith(expect.objectContaining({ id: 'james' }));
    expect(previewTeacher).toHaveBeenCalledWith(expect.objectContaining({ id: 'james' }));
    teacher.unmount();
  });
});
