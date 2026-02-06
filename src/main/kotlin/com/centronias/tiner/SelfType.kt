package com.centronias.tiner

interface SelfType<Self : SelfType<Self>>

@Suppress("UNCHECKED_CAST")
inline val <Self : SelfType<Self>> SelfType<Self>.self get() = this as Self