package com.dank420girl.core.config;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class MenuSlotsTest {
    @Test
    public void parsesRangesAndLists() {
        Problems problems = new Problems("config.yml");
        assertArrayEquals(new int[] {9, 10, 11}, MenuSettings.slots("9-11", 54, Collections.<Integer>emptySet(), problems));
        assertArrayEquals(new int[] {10, 12, 19, 20}, MenuSettings.slots(Arrays.asList(10, "12", "19-20"), 54,
                Collections.<Integer>emptySet(), problems));
        assertArrayEquals(new int[] {9, 11}, MenuSettings.slots("9-11", 54, new HashSet<Integer>(Arrays.asList(10)), problems));
        assertEquals(0, problems.size());
    }

    @Test
    public void reportsBadSlots() {
        Problems problems = new Problems("config.yml");
        int[] slots = MenuSettings.slots("x, 60-70", 9, Collections.singleton(4), problems);
        assertEquals(8, slots.length);
        assertEquals(2, problems.size());
    }
}
