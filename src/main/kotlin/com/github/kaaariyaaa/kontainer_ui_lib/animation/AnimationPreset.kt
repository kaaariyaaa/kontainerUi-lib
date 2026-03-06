package com.github.kaaariyaaa.kontainer_ui_lib.animation

enum class AnimationPreset {
    STAGGER,
    SNAKE,
    RANDOM,
}

fun stagger(): AnimationPreset = AnimationPreset.STAGGER

fun snake(): AnimationPreset = AnimationPreset.SNAKE

fun random(): AnimationPreset = AnimationPreset.RANDOM
