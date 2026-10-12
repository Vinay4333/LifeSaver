package app.lifesaver.ui

import app.lifesaver.policy.Approval

class ApproveActions(
    val onCreateKey: () -> Unit,
    val onCopyKey: () -> Unit,
    val onCheckLink: (String) -> Unit,
    val onApprove: (Approval) -> Unit,
    val onDismiss: () -> Unit,
)
