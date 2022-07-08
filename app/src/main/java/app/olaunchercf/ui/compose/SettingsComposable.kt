package app.olaunchercf.ui.compose

import SettingsTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import app.olaunchercf.R
import app.olaunchercf.data.EnumOption
import app.olaunchercf.style.CORNER_RADIUS

object SettingsComposable {
    @Composable
    fun SettingsArea (
        title: String,
        items: Array<@Composable (MutableState<Boolean>, (Boolean) -> Unit ) -> Unit>
    ) {
        val selected = remember { mutableStateOf(-1) }
        Column(
            modifier = Modifier
                .padding(12.dp, 12.dp, 12.dp, 0.dp)
                .background(SettingsTheme.color.settings, SettingsTheme.shapes.settings)
                .border(
                    0.5.dp,
                    colorResource(R.color.blackInverseTrans50),
                    RoundedCornerShape(CORNER_RADIUS),
                )
                .padding(20.dp)
                .fillMaxWidth()
        ) {
            SettingsTitle(text = title)
            items.forEachIndexed { i, item ->
                item(mutableStateOf(i == selected.value)) { b -> selected.value = if (b) i else -1 }
            }
        }
    }

    @Composable
    fun SettingsTitle(text: String) {
        Text(
            text = text,
            style = SettingsTheme.typography.title,
            modifier = Modifier
                .padding(0.dp, 0.dp, 0.dp, 12.dp)
        )
    }

    @Composable
    fun SettingsToggle(
        title: String,
        state: MutableState<Boolean>,
        onChange: (Boolean) -> Unit,
        onToggle: () -> Unit
    ) {
        val buttonText = if (state.value) stringResource(R.string.on) else stringResource(R.string.off)
        SettingsRow(
            title = title,
            onClick = {
                onChange(false)
                state.value = !state.value
                onToggle()
            },
            buttonText = buttonText
        )
    }

    @Composable
    fun <T: EnumOption> SettingsItem(
        title: String,
        currentSelection: MutableState<T>,
        values: Array<T>,
        open: MutableState<Boolean>,
        onChange: (Boolean) -> Unit,
        onSelect: (T) -> Unit,
    ) {
        if (open.value) {
            Box(
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectTapGestures {
                            onChange(false)
                        }
                    }
                    .onFocusEvent {
                        if (it.isFocused) {
                            onChange(false)
                        }
                    }
            ) {
                SettingsSelector(values) { i ->
                    onChange(false)
                    currentSelection.value = i
                    onSelect(i)
                }
            }
        } else {
            SettingsRow(
                title = title,
                onClick = { onChange(true) },
                buttonText = currentSelection.value.string()
            )
        }
    }

    @Composable
    fun SettingsNumberItem(
        title: String,
        currentSelection: MutableState<Int>,
        min: Int,
        max: Int,
        open: MutableState<Boolean>,
        onChange: (Boolean) -> Unit,
        onSelect: (Int) -> Unit
    ) {
        if (open.value) {
            SettingsNumberSelector(
                number = currentSelection,
                min = min,
                max = max,
            ) { i ->
                onChange(false)
                currentSelection.value = i
                onSelect(i)
            }
        } else {
            SettingsRow(
                title = title,
                onClick = { onChange(true) },
                buttonText = currentSelection.value.toString()
            )
        }
    }

    @Composable
    fun SettingsAppSelector(
        title: String,
        currentSelection: MutableState<String>,
        onClick: () -> Unit,
    ) {
        SettingsRow(
            title = title,
            onClick = onClick,
            buttonText = currentSelection.value
        )
    }

    @Composable
    private fun SettingsRow(
        title: String,
        onClick: () -> Unit,
        buttonText: String,
    ) {
        ConstraintLayout(modifier = Modifier.fillMaxWidth()) {

            val (text, button) = createRefs()

            Box(
                modifier = Modifier
                    .constrainAs(text) {
                        start.linkTo(parent.start)
                        end.linkTo(button.start)
                        top.linkTo(parent.top)
                        bottom.linkTo(parent.bottom)
                        width = Dimension.fillToConstraints
                    },
            ) {
                Text(
                    title,
                    style = SettingsTheme.typography.item,
                modifier = Modifier.align(Alignment.CenterStart)
                )
            }

            TextButton(
                onClick = onClick,
                modifier = Modifier.constrainAs(button) {
                    end.linkTo(parent.end)
                    top.linkTo(parent.top)
                    bottom.linkTo(parent.bottom)
                },
            ) {
                Text(
                    text = buttonText,
                    style = SettingsTheme.typography.button
                )
            }
        }
    }

    @Composable
    private fun <T: EnumOption> SettingsSelector(options: Array<T>, onSelect: (T) -> Unit) {
        Box(
            modifier = Modifier
                .background(SettingsTheme.color.selector, SettingsTheme.shapes.settings)
                .fillMaxWidth()
        ) {
            LazyRow(
                modifier = Modifier
                    .align(Alignment.CenterEnd),
                horizontalArrangement = Arrangement.SpaceEvenly
            )
            {
                for (opt in options) {
                    item {
                        TextButton(
                            onClick = { onSelect(opt) },
                        ) {
                            Text(
                                text = opt.string(),
                                style = SettingsTheme.typography.button
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SettingsNumberSelector(
        number: MutableState<Int>,
        min: Int,
        max: Int,
        onCommit: (Int) -> Unit
    ) {
        ConstraintLayout(
            modifier = Modifier
                .background(SettingsTheme.color.selector, SettingsTheme.shapes.settings)
                .fillMaxWidth()
        ) {
            val (plus, minus, text, button) = createRefs()
            TextButton(
                onClick = { if (number.value < max) number.value += 1 },
                modifier = Modifier.constrainAs(plus) {
                    top.linkTo(parent.top)
                    bottom.linkTo(parent.bottom)
                    start.linkTo(parent.start)
                    end.linkTo(text.start)
                },
            ) {
                Text("+", style = SettingsTheme.typography.button)
            }
            Text(
                text = number.value.toString(),
                modifier = Modifier
                    .fillMaxHeight()
                    .constrainAs(text) {
                        top.linkTo(parent.top)
                        bottom.linkTo(parent.bottom)
                        start.linkTo(plus.end)
                        end.linkTo(minus.start)
                    },
                style = SettingsTheme.typography.item,
            )
            TextButton(
                onClick = { if (number.value > min) number.value -= 1 },
                modifier = Modifier.constrainAs(minus) {
                    top.linkTo(parent.top)
                    bottom.linkTo(parent.bottom)
                    start.linkTo(text.end)
                    end.linkTo(button.start)
                },
            ) {
                Text("-", style = SettingsTheme.typography.button)
            }
            TextButton(
                onClick = { onCommit(number.value) },
                modifier = Modifier.constrainAs(button) {
                    top.linkTo(parent.top)
                    bottom.linkTo(parent.bottom)
                    start.linkTo(minus.end)
                    end.linkTo(parent.end)
                },
            ) {
                Text(stringResource(R.string.commit), style = SettingsTheme.typography.button)
            }
        }
    }
}