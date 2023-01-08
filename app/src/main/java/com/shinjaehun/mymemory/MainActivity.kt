package com.shinjaehun.mymemory

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.shinjaehun.mymemory.models.BoardSize
import com.shinjaehun.mymemory.models.MemoryCard
import com.shinjaehun.mymemory.models.MemoryGame
import com.shinjaehun.mymemory.utils.DEFAULT_ICONS

class MainActivity : AppCompatActivity() {

    companion object val TAG = "MainActivity"

    private lateinit var rvBoard: RecyclerView
    private lateinit var tvNumMoves: TextView
    private lateinit var tvNumPairs: TextView

    private var boardSize: BoardSize = BoardSize.EASY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rvBoard = findViewById(R.id.rvBoard)
        tvNumMoves = findViewById(R.id.tvNumMoves)
        tvNumPairs = findViewById(R.id.tvNumPairs)

        val memoryGame = MemoryGame(boardSize)

//        val chosenImages = DEFAULT_ICONS.shuffled().take(boardSize.getNumPairs()) // 카드 한벌 shuffled, pairs만큼
//        val randomizedImages = (chosenImages + chosenImages).shuffled() // 카드 두 벌 shuffled
//        // val memoryCards = randomizedImages.map { MemoryCard(it, false, false) } // 같은 거...(default value)
//        val memoryCards = randomizedImages.map { MemoryCard(it) } // 이런 식으로 Image lists를 class list로 map 가능!!!

//        rvBoard.adapter = MemoryBoardAdapter(this, boardSize, randomizedImages)
//        rvBoard.adapter = MemoryBoardAdapter(this, boardSize, memoryCards)
//        rvBoard.adapter = MemoryBoardAdapter(this, boardSize, memoryGame.cards)
        rvBoard.adapter = MemoryBoardAdapter(this, boardSize, memoryGame.cards, object: MemoryBoardAdapter.CardClickListener{
            override fun onCardClicked(position: Int) {
                Log.i(TAG, "Card clicked $position")
            }
        })

        rvBoard.setHasFixedSize(true)
        rvBoard.layoutManager = GridLayoutManager(this, boardSize.getWidth())
    }
}