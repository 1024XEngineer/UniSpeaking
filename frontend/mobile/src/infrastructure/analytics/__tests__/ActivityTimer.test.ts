import { createActivityTimer } from '../ActivityTimer';

describe('createActivityTimer', () => {
  it('excludes paused and hidden time from effective duration', () => {
    let current = 0;
    const timer = createActivityTimer(() => current);

    timer.start();
    current = 4_000;
    timer.pause();
    current = 14_000;
    timer.resume();
    current = 19_000;
    timer.setVisible(false);
    current = 29_000;
    timer.setVisible(true);
    current = 32_000;

    expect(timer.stop()).toBe(12);
  });
});
