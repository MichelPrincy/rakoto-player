package com.rakotoplayer.android;

import java.util.*;

/** One shuffled cycle visits every item before repeating, with real previous-track history. */
public final class ShuffleCycle {
  private final Random random = new Random();
  private final List<Integer> remaining = new ArrayList<>(), history = new ArrayList<>();
  private int cursor, size;

  public void reset(int count, int current) {
    size = count;
    remaining.clear();
    history.clear();
    cursor = 0;
    for (int i = 0; i < count; i++) if (i != current) remaining.add(i);
    if (current >= 0) history.add(current);
  }

  public int next(int current, int direction, int count, boolean repeat) {
    if (count < 1) return -1;
    if (history.isEmpty() || count < size) reset(count, current);
    else if (count > size) {
      for (int i = size; i < count; i++) remaining.add(i);
      size = count;
    }
    if (direction < 0) {
      if (cursor > 0) cursor--;
      return history.isEmpty() ? current : history.get(cursor);
    }
    if (cursor + 1 < history.size()) return history.get(++cursor);
    if (remaining.isEmpty()) {
      if (!repeat) return -1;
      reset(count, current);
      if (remaining.isEmpty()) return current;
    }
    int selected = remaining.remove(random.nextInt(remaining.size()));
    history.add(selected);
    cursor = history.size() - 1;
    return selected;
  }
}
