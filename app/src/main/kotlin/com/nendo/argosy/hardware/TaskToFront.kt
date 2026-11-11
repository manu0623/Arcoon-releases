package com.nendo.argosy.hardware

import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.Context
import com.nendo.argosy.util.ArcoonTrace

/**
 * Raises one of Argosy's own tasks through the activity manager.
 *
 * A start into a task that is already in back (`REORDER_TO_FRONT`, `SINGLE_TOP`) or an
 * `AppTask.moveToFront()` is a task-to-front, and the platform plays its own transition for it
 * whatever the intent asks for. The activity manager's own move-to-front takes a launch-options
 * bundle, and this is the call that leaves the swap without the system's sideways slide on the
 * handhelds we ship on; the callers keep their previous path as the fallback for a build where
 * the move is refused.
 */
internal object TaskToFront {

    /** Moves [taskId] to the front. Returns false when the platform refused the move. */
    fun move(context: Context, taskId: Int): Boolean {
        val manager = context.getSystemService(ActivityManager::class.java) ?: return false
        return runCatching {
            manager.moveTaskToFront(
                taskId,
                ActivityManager.MOVE_TASK_NO_USER_ACTION,
                ActivityOptions.makeCustomAnimation(context, 0, 0).toBundle()
            )
            ArcoonTrace.log("task to front: moved task $taskId")
            true
        }.getOrElse { error ->
            ArcoonTrace.log(
                "task to front: move of task $taskId refused " +
                    "(${error.javaClass.simpleName}: ${error.message})"
            )
            false
        }
    }

    /** The id of the task whose top or base activity is one of [activities], or null. */
    fun taskFor(context: Context, vararg activities: Class<*>): Int? {
        val manager = context.getSystemService(ActivityManager::class.java) ?: return null
        val names = activities.mapTo(mutableSetOf()) { it.name }
        return manager.appTasks.firstNotNullOfOrNull { task ->
            val info = runCatching { task.taskInfo }.getOrNull() ?: return@firstNotNullOfOrNull null
            val holds = info.topActivity?.className?.let { it in names } == true ||
                info.baseActivity?.className?.let { it in names } == true
            if (holds) info.taskId else null
        }
    }
}
