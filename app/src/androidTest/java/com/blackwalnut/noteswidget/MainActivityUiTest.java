package com.blackwalnut.noteswidget;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertNotNull;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class MainActivityUiTest {
    @Test
    public void showsNoteListAndPrimaryActions() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertNotNull(activity.findViewById(R.id.note_list));
                assertNotNull(activity.findViewById(R.id.button_new_note));
                assertNotNull(activity.findViewById(R.id.button_add_widget));
            });
        }
    }
}
