package io.github.saadahmed1122.bibi

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.saadahmed1122.bibi.core.designsystem.BibiTheme

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Column(Modifier.padding(padding).padding(24.dp)) {
            Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.home_body), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Preview
@Composable
private fun HomeScreenPreview() {
    BibiTheme { HomeScreen() }
}
