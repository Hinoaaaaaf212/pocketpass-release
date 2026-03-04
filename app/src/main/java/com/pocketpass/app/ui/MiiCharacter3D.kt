package com.pocketpass.app.ui

import com.pocketpass.app.data.Encounter
import kotlin.random.Random

/**
 * Represents a 3D Mii character roaming in the plaza
 */
data class MiiCharacter3D(
    val encounter: Encounter,
    var x: Float,              // X position in 3D space
    var y: Float = 0f,         // Y position (height, usually 0 for ground)
    var z: Float,              // Z position in 3D space
    var rotation: Float = 0f,  // Rotation angle in degrees (0-360)
    var velocityX: Float = 0f, // Movement velocity X
    var velocityZ: Float = 0f, // Movement velocity Z
    var isWalking: Boolean = false,
    var walkTimer: Float = 0f,  // Time until next direction change
    var animationTime: Float = 0f // For walk cycle animation
) {
    companion object {
        const val WALK_SPEED = 0.5f
        const val PLAZA_SIZE = 10f // Size of the plaza area

        /**
         * Create a Mii at a random position in the plaza
         */
        fun createRandom(encounter: Encounter): MiiCharacter3D {
            return MiiCharacter3D(
                encounter = encounter,
                x = Random.nextFloat() * PLAZA_SIZE - PLAZA_SIZE / 2,
                z = Random.nextFloat() * PLAZA_SIZE - PLAZA_SIZE / 2,
                rotation = Random.nextFloat() * 360f
            )
        }
    }

    /**
     * Update Mii position and animation
     */
    fun update(deltaTime: Float) {
        walkTimer -= deltaTime

        // Time to change direction?
        if (walkTimer <= 0) {
            changeDirection()
            walkTimer = Random.nextFloat() * 3f + 2f // 2-5 seconds
        }

        if (isWalking) {
            // Update position
            x += velocityX * deltaTime
            z += velocityZ * deltaTime

            // Update animation time
            animationTime += deltaTime

            // Keep within plaza bounds
            val halfSize = PLAZA_SIZE / 2
            if (x < -halfSize || x > halfSize || z < -halfSize || z > halfSize) {
                // Bounce back
                x = x.coerceIn(-halfSize, halfSize)
                z = z.coerceIn(-halfSize, halfSize)
                changeDirection()
            }

            // Update rotation to face movement direction
            if (velocityX != 0f || velocityZ != 0f) {
                rotation = Math.toDegrees(kotlin.math.atan2(velocityX.toDouble(), velocityZ.toDouble())).toFloat()
            }
        }
    }

    /**
     * Randomly change walking direction
     */
    private fun changeDirection() {
        val shouldWalk = Random.nextFloat() > 0.3f // 70% chance to walk

        if (shouldWalk) {
            isWalking = true
            val angle = Random.nextFloat() * 2 * Math.PI
            velocityX = kotlin.math.cos(angle).toFloat() * WALK_SPEED
            velocityZ = kotlin.math.sin(angle).toFloat() * WALK_SPEED
        } else {
            isWalking = false
            velocityX = 0f
            velocityZ = 0f
        }
    }
}
