package com.dhj.actinium.resourcepack.entity.mixinterface;

/** Reads the villager's assigned trade career without initializing trades. */
public interface VillagerCareerAccess {
    /** Returns the saved career ID, or zero before a career is assigned. */
    int actinium$getCareerId();
}
