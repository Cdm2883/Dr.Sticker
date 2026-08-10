package vip.cdms.drsticker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import vip.cdms.drsticker.ui.screens.MainScreen
import vip.cdms.drsticker.ui.theme.AppTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    // TODO: delete
//    @Inject
//    lateinit var rulesetRepository: RulesetRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // TODO: delete
//        rulesetRepository.addRuleset(
//            displayName = "QQ (Official)",
//            description = "The official ruleset for QQ.",
//            condition = ActivityNameCondition(
//                activityName = "com.tencent.mobileqq.activity.SplashActivity"
//            ),
//            trigger = FloatingButtonTrigger(),
//            preprocesses = emptyList(),
//            adapter = AccessibilityDropAdapter()
//        )

        setContent {
            AppTheme {
                MainScreen()
            }
        }
    }
}
