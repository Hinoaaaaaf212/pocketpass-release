package com.pocketpass.app.rendering

/**
 * Animation state machine for plaza Miis.
 * Maps Mii behavioral states to animation file names from the extracted 3DS assets.
 */
object MiiAnimationController {

    enum class AnimState {
        IDLE,
        WALKING,
        GREETING,
        HAPPY,
        SLEEPY,
        ANGRY,
        TALKING
    }

    /**
     * Returns the animation GLB filename for a given state.
     * Multiple options are provided for variety — caller picks randomly.
     */
    fun getAnimationsForState(state: AnimState): List<String> {
        return when (state) {
            AnimState.WALKING -> listOf(
                "mii_hand_walk.glb",
                "mii_hand_walk_f.glb"
            )
            AnimState.IDLE -> listOf(
                "mii_hand_wait.glb",
                "mii_hand_wait_b.glb",
                "mii_hand_wait_c.glb"
            )
            AnimState.GREETING -> listOf(
                "mii_hand_greeting.glb",
                "mii_hand_greeting_end.glb"
            )
            AnimState.HAPPY -> listOf(
                "mii_hand_appeal.glb",
                "mii_hand_pleasure.glb",
                "mii_hand_pleasure_l.glb"
            )
            AnimState.SLEEPY -> listOf(
                "mii_hand_sleep.glb",
                "mii_hand_sleep_start.glb"
            )
            AnimState.ANGRY -> listOf(
                "mii_hand_angry.glb",
                "mii_hand_angry_start.glb"
            )
            AnimState.TALKING -> listOf(
                "mii_hand_clasp_loop.glb",
                "mii_hand_talk_a.glb",
                "mii_hand_talk_b.glb"
            )
        }
    }

    /**
     * Map mood string (from UserPreferences) to AnimState.
     */
    fun moodToAnimState(mood: String): AnimState {
        return when (mood.uppercase()) {
            "HAPPY" -> AnimState.HAPPY
            "SLEEPY" -> AnimState.SLEEPY
            "ANGRY" -> AnimState.ANGRY
            "CHATTY" -> AnimState.TALKING
            else -> AnimState.IDLE
        }
    }

    /**
     * The cross-fade duration in seconds for blending between animations.
     */
    const val CROSSFADE_DURATION = 0.3f

    // Plaza animation files used for multi-animation merge
    const val PLAZA_WALK_FILE = "mii_hand_walk.glb"
    const val PLAZA_IDLE_FILE = "mii_hand_wait.glb"
    const val PLAZA_GREETING_FILE = "mii_hand_greeting.glb"
}
