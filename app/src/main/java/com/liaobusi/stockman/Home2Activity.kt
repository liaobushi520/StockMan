package com.liaobusi.stockman

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.liaobbusi.myapplication.ui.theme.MyApplicationTheme

class Home2Activity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {

            MyApplicationTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FunctionList(
                        functions = listOf(
                            Item("策略", Intent()),
                            Item("策略", Intent()),
                            Item("策略", Intent()),
                            Item("策略", Intent())
                        )
                    )
                }
            }
        }
    }


}

data class Item(val name: String, val intent: Intent)

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    FunctionList(
        functions = listOf(
            Item("策略", Intent()),
            Item("策略", Intent()),
            Item("策略", Intent()),
            Item("策略", Intent())
        )
    )
}


@Composable
fun FunctionList(functions: List<Item>) {

    Column() {
        functions.forEach { item ->
            Button(onClick = { }) {
                    Text(text = item.name)
            }
        }
    }

}