package eu.kanade.presentation.more.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import eu.kanade.domain.tutorial.TutorialPreferences
import eu.kanade.domain.tutorial.model.TutorialMode
import eu.kanade.presentation.tutorial.CoachTipRegistry
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Onboarding step: asks the user how experienced they are so we can either run
 * the beginner guided tour or stay out of the way for power users.
 */
internal class ExperienceStep : OnboardingStep {

    private val tutorialPreferences: TutorialPreferences = Injekt.get()

    override val isComplete: Boolean = true

    @Composable
    override fun Content() {
        var selected by remember {
            mutableStateOf(tutorialPreferences.tutorialMode().get())
        }

        fun choose(mode: TutorialMode) {
            selected = mode
            tutorialPreferences.tutorialMode().set(mode)
            if (mode == TutorialMode.OFF) {
                // Power user: skip the whole tour up front.
                tutorialPreferences.tourCompleted().set(true)
                tutorialPreferences.shownTips().set(
                    CoachTipRegistry.tips.map { it.id }.toSet(),
                )
            } else {
                tutorialPreferences.tourCompleted().set(false)
            }
        }

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(MR.strings.onboarding_experience_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(MR.strings.onboarding_experience_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            ExperienceOption(
                label = stringResource(MR.strings.onboarding_experience_beginner),
                selected = selected == TutorialMode.GUIDED,
                onClick = { choose(TutorialMode.GUIDED) },
            )
            ExperienceOption(
                label = stringResource(MR.strings.onboarding_experience_expert),
                selected = selected == TutorialMode.OFF,
                onClick = { choose(TutorialMode.OFF) },
            )
        }
    }

    @Composable
    private fun ExperienceOption(
        label: String,
        selected: Boolean,
        onClick: () -> Unit,
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(
                    selected = selected,
                    onClick = onClick,
                    role = Role.RadioButton,
                )
                .padding(vertical = MaterialTheme.padding.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RadioButton(selected = selected, onClick = null)
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
