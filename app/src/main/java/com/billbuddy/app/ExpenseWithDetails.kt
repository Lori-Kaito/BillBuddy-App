package com.billbuddy.app

import java.util.Date

data class ExpenseWithDetails(
    val id: Long,
    val title: String,
    val amount: Double,
    val categoryId: Long,
    val date: Date,
    val notes: String?,
    val receiptImagePath: String?,
    val isPersonal: Boolean,
    val groupId: Long?,
    val paidById: Long?,
    val createdAt: Date,
    val updatedAt: Date,
    val categoryName: String,
    val categoryIcon: String?,
    val categoryColor: String?
)