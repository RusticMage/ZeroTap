package com.zerotap.domain.incident

import com.zerotap.domain.model.Incident
import com.zerotap.domain.model.IncidentStatus
import com.zerotap.util.Logger

class IncidentStateMachine {
    fun transition(incident: Incident, nextStatus: IncidentStatus): Incident? {
        val currentStatus = incident.status
        val isValid = when (currentStatus) {
            IncidentStatus.DETECTED -> nextStatus == IncidentStatus.ACTIVE || nextStatus == IncidentStatus.ALERTING || nextStatus == IncidentStatus.DISMISSED
            IncidentStatus.ACTIVE -> nextStatus == IncidentStatus.ALERTING || nextStatus == IncidentStatus.DISMISSED
            IncidentStatus.ALERTING -> nextStatus == IncidentStatus.RESOLVED || nextStatus == IncidentStatus.DISMISSED
            IncidentStatus.RESOLVED, IncidentStatus.DISMISSED -> false
            else -> nextStatus == IncidentStatus.DISMISSED
        }

        return if (isValid) {
            Logger.incident("StateMachine", "Transitioning incident ${incident.id} from $currentStatus to $nextStatus")
            incident.copy(status = nextStatus)
        } else {
            Logger.incident("StateMachine", "Invalid transition for incident ${incident.id} from $currentStatus to $nextStatus")
            null
        }
    }
}
