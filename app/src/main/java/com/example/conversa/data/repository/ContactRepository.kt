package com.example.conversa.data.repository

import com.example.conversa.data.remote.ApiService
import com.example.conversa.model.Contact

class ContactRepository(private val apiService: ApiService) {
    suspend fun getContacts(): List<Contact> {
        return apiService.getContacts()
    }
}