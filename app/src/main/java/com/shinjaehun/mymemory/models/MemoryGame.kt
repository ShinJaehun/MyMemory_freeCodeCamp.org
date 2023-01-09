package com.shinjaehun.mymemory.models

import com.shinjaehun.mymemory.utils.DEFAULT_ICONS

class MemoryGame(private val boardSize: BoardSize){

    val cards: List<MemoryCard>
    var numPairsFound = 0

    private var numCardFlips = 0

    private var indexOfSingleSelectedCard: Int? = null; // 카드를 뒤집지 않은 상태에서는 null

    // 초기화하면서 카드 페어에 맞게 이미지 섞어 선택하고, 두 벌을 다시 섞음. 이미지를 map으로 MemoryCard 생성(이미지 resource id가 MemoryCard의 identifier
    init {
        val chosenImages = DEFAULT_ICONS.shuffled().take(boardSize.getNumPairs())
        val randomizedImages = (chosenImages + chosenImages).shuffled()
        cards = randomizedImages.map { MemoryCard(it) }
    }

    fun flipCard(position: Int): Boolean {
        numCardFlips++
        val card = cards[position]
        // Three cases:
        // 0 cards previously flipped over -> flip over the selected card : 사실 세번째 case와 동일! (restore cards) + flip over the selected card
        // 1 card previously flipped over -> flip over the selected card + check if the images match
        // 2 cards previously flipped over -> restore cards + flip over the selected card
        var foundMatch = false
        if(indexOfSingleSelectedCard == null) {
            // 0 or 2 cards previously flipped over
            restoreCards()
            indexOfSingleSelectedCard = position
        } else {
            // exactly 1 card previously flipped over
            foundMatch = checkForMatch(indexOfSingleSelectedCard!!, position) // 근데 여기에 '!!'를 하는 이유는 잘 모르겠음!
            indexOfSingleSelectedCard = null
        }
        card.isFaceUp = !card.isFaceUp
        return foundMatch
    }

    private fun checkForMatch(position1: Int, position2: Int): Boolean {
        if (cards[position1].identifier != cards[position2].identifier) {
            return false
        }
        cards[position1].isMatched = true
        cards[position2].isMatched = true
        numPairsFound++
        return true
    }

    private fun restoreCards() {
        for (card in cards) {
            if (!card.isMatched) {
                card.isFaceUp = false
            }
        }
    }

    fun haveWonGame(): Boolean {
        return numPairsFound == boardSize.getNumPairs()
    }

    fun isCardFaceUp(position: Int): Boolean {
        return cards[position].isFaceUp
    }

    fun getNumMoves(): Int {
        return numCardFlips / 2
    }
}