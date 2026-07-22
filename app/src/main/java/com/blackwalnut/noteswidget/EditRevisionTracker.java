package com.blackwalnut.noteswidget;

import java.util.Objects;

final class EditRevisionTracker {
    static final class Attempt {
        final long revision;
        final String content;

        Attempt(long revision, String content) {
            this.revision = revision;
            this.content = content;
        }
    }

    private long revision;
    private long savedRevision;
    private String savedContent = "";

    void reset(String content) {
        revision = 0;
        savedRevision = 0;
        savedContent = content;
    }

    void changed() { revision += 1; }

    boolean isDirty() { return revision > savedRevision; }

    Attempt begin(String content) {
        if (!isDirty()) return null;
        if (Objects.equals(savedContent, content)) {
            savedRevision = revision;
            return null;
        }
        return new Attempt(revision, content);
    }

    void complete(Attempt attempt) {
        if (attempt.revision < savedRevision) return;
        savedRevision = attempt.revision;
        savedContent = attempt.content;
    }
}
