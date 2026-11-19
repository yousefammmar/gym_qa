package com.gymtracker.model;

/** What a goal measures. Progress is always calculated from stored workouts (rule GOL-06). */
public enum GoalType {
    /** number of completed workouts since the goal's start date */
    WORKOUT_COUNT,
    /** kg x reps of completed sets since the start date */
    TOTAL_VOLUME,
    /** heaviest completed set of one exercise since the start date */
    EXERCISE_MAX_WEIGHT
}
