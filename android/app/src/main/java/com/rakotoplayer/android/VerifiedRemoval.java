package com.rakotoplayer.android;

import java.io.IOException;

/** Makes the data-loss boundary explicit: failed verification cannot produce a removal ticket. */
public final class VerifiedRemoval {
  public interface Check {
    void run() throws Exception;
  }

  public interface Remove {
    boolean run() throws Exception;
  }

  private VerifiedRemoval() {}

  public static Ticket verify(Check check) throws Exception {
    check.run();
    return new Ticket();
  }

  public static final class Ticket {
    private boolean used;

    private Ticket() {}

    public void remove(Remove remove, Check committed) throws Exception {
      if (used) throw new IllegalStateException("Suppression déjà traitée");
      used = true;
      if (!remove.run())
        throw new IOException("Android n’a pas confirmé la suppression de l’original");
      committed.run();
    }
  }
}
