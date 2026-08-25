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

  it('guards duplicate lifecycle calls while hidden and paused', () => {
    let current = 0;
    const timer = createActivityTimer(() => current);
    expect(timer.pause()).toBe(0);
    timer.resume();
    timer.setVisible(false);
    timer.setVisible(false);
    timer.start();
    timer.start();
    timer.resume();
    current = 5_000;
    expect(timer.pause()).toBe(0);
    expect(timer.pause()).toBe(0);
    timer.resume();
    timer.setVisible(true);
    expect(timer.stop()).toBe(0);
  });
});
