package com.borges.minesweeper

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.*
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.Button
import android.widget.Toast
import com.borges.minesweeper.data.Cell
import com.borges.minesweeper.data.Grid
import com.borges.minesweeper.data.GridDifficulty
import com.borges.minesweeper.dialogs.DifficultySelectorDialog
import com.borges.minesweeper.fragments.EasyGridFragment
import com.borges.minesweeper.fragments.HardGridFragment
import com.borges.minesweeper.fragments.MediumGridFragment
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*
import kotlin.concurrent.scheduleAtFixedRate

class MainActivity : AppCompatActivity(), DifficultySelectorDialog.ChangeDifficultyListener {
    private val manager = supportFragmentManager
    private val vibrator: Vibrator
        get() = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    private var selectedDifficulty = GridDifficulty.MEDIUM
    private var bombsLeft = selectedDifficulty.bombCount

    private lateinit var grid: Grid
    private var totalTime = 0
    private val handler = Handler()
    private val runnable = Runnable {
        totalTime += 1
        textTimer.text = totalTime.toString()
        tickTimer()
    }

    private val mineColor by lazy { resources.getColor(R.color.colorMineCell, null) }
    private val concealedColor by lazy { resources.getColor(R.color.colorConcealedCell, null) }
    private val successColor by lazy { resources.getColor(R.color.colorPrimary, null) }

    private fun buttonId(x: Int, y: Int): String = "button_${x}_$y"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(minesweepr_toolbar)

        updateDifficulty()
        buttonDifficulty.setOnClickListener { openDifficultyDialog() }
    }

    private fun openDifficultyDialog() {
        val dialog = DifficultySelectorDialog()
        val bundle = Bundle()
        bundle.putString("difficulty", selectedDifficulty.label)
        dialog.arguments = bundle
        dialog.show(supportFragmentManager, "Select Difficulty")
    }

    override fun onDifficultyChanged(difficulty: GridDifficulty) {
        selectedDifficulty = difficulty
        updateDifficulty()
    }

    private fun updateDifficulty() {
        val fragment = when(selectedDifficulty) {
            GridDifficulty.EASY -> EasyGridFragment()
            GridDifficulty.MEDIUM -> MediumGridFragment()
            GridDifficulty.HARD -> HardGridFragment()
        }
        val transaction = manager.beginTransaction()
        transaction.replace(R.id.fragment_holder, fragment)
        transaction.addToBackStack(null)
        transaction.commit()

        grid = Grid(
            width = selectedDifficulty.width,
            height = selectedDifficulty.height,
            bombCount = selectedDifficulty.bombCount
        )
        updateBombsLeftLabel()
        buttonDifficulty.text = selectedDifficulty.label
    }

    private fun newGame() {
        performActionOnAllButtons {
            it.text = ""
            it.setBackgroundColor(concealedColor)
            it.setTextColor(Color.WHITE)
            it.isEnabled = true
        }
        grid.reset()
        resetTimer()
        bombsLeft = selectedDifficulty.bombCount
        updateBombsLeftLabel()
    }

    private fun updateBombsLeftLabel() {
        bombsLeft = if (grid.isInitialized)
            selectedDifficulty.bombCount - grid.markedBombs
        else
            selectedDifficulty.bombCount
        textBombsLeft.text = bombsLeft.toString()
    }

    private fun performActionOnAllButtons(action: (Button) -> Unit) {
        repeat(selectedDifficulty.width) { x ->
            repeat(selectedDifficulty.height) { y ->
                val button: Button = findViewById(resources.getIdentifier(buttonId(x, y), "id", packageName))
                action(button)
            }
        }
    }

    private fun gameWon() {
        handler.removeCallbacks(runnable)
        highlightBombs()
        Toast.makeText(this, "YOU WIN!!", Toast.LENGTH_SHORT).show()
        disableGrid()
    }

    private fun gameLost() {
        handler.removeCallbacks(runnable)
        revealAllCells()
        Toast.makeText(this, "YOU LOSE", Toast.LENGTH_SHORT).show()
        disableGrid()
    }

    private fun disableGrid() {
        performActionOnAllButtons { it.isEnabled = false }
    }

    private fun initializeButtonGrid() {
        performActionOnAllButtons {

            it.setOnLongClickListener { view ->
                vibrator.vibrate(VibrationEffect.createOneShot(100, 100))

                if (grid.isInitialized) {
                    val background = view.background as ColorDrawable
                    val cell = findCell(resources.getResourceName(view.id))

                    if (!cell.isRevealed) {
                        when (background.color) {
                            mineColor -> {
                                cell.unMarkBomb()
                                view.setBackgroundColor(concealedColor)
                            }
                            concealedColor -> {
                                cell.markAsBomb()
                                view.setBackgroundColor(mineColor)
                                if (grid.isClean) gameWon()
                            }
                        }
                        updateBombsLeftLabel()
                    }

                } else clickCell(it)

                true
            }
        }
    }

    fun clickCell(view: View) {
        if (grid.isInitialized) {
            val cell = findCell(resources.getResourceName(view.id))
            revealCell(cell, view as Button)

            when {
                cell.isBomb -> gameLost()
                cell.hasNoNeighboringBombs -> clickNeighbors(cell)
                grid.isClean -> gameWon()
            }

        } else {
            val safeCoordinate = findButtonCoordinate(resources.getResourceName(view.id))
            startNewGame(view, safeCoordinate)
        }
    }

    private fun startNewGame(view: View, safeCoordinate: Pair<Int, Int>) {
        grid.generateGrid(safeCoordinate)
        initializeButtonGrid()
        buttonNewGame.isEnabled = true
        buttonNewGame.setOnClickListener { newGame() }
        clickCell(view)
        tickTimer()
    }

    private fun tickTimer() {
        handler.postDelayed(runnable, 1000)
    }

    private fun resetTimer() {
        handler.removeCallbacks(runnable)
        totalTime = 0
        textTimer.text = totalTime.toString()
    }

    private fun clickNeighbors(cell: Cell) {
        val neighbors = grid.neighbors(cell.locationX, cell.locationY)
        neighbors.filter { !it.isRevealed }.forEach { neighbor ->
            val neighborButton: Button = findViewById(resources.getIdentifier(buttonId(neighbor.locationX, neighbor.locationY), "id", packageName))
            clickCell(neighborButton)
        }
    }

    private fun highlightBombs() {
        performActionOnAllButtons {
            val cell = findCell(resources.getResourceName(it.id))
            if (cell.isBomb)
                it.setBackgroundColor(successColor)
        }
    }

    private fun revealAllCells() {
        performActionOnAllButtons {
            val cell = findCell(resources.getResourceName(it.id))
            revealCell(cell, it)
        }
    }

    private fun findCell(resourceName: String): Cell {
        val coordinate = findButtonCoordinate(resourceName)
        return grid.cell(coordinate)
    }

    private fun findButtonCoordinate(resourceName: String): Pair<Int, Int> {
        val location = resourceName.substringAfter('_')
        val x = location[0].minus('0')
        val y = location.substring(2).toInt()
        return x to y
    }

    private fun revealCell(cell: Cell, button: Button) {
        button.text = cell.toString()
        button.setBackgroundColor(resources.getColor(cell.backgroundColor, null))
        button.setTextColor(resources.getColor(cell.textColor, null))
        cell.isRevealed = true
    }

}
