package com.gymtracker.model;

/** Display state derived from status, progress and dates (rule GOL-10). Never stored. */
public enum GoalState {
    ACTIVE, ACHIEVED, OVERDUE, COMPLETED
}
