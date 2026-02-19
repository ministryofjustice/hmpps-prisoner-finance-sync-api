package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.controllers

const val VALIDATION_REGEX_PRISON_ID = "^[A-Za-z0-9]{3}$"
const val VALIDATION_MESSAGE_PRISON_ID = "prisonId must be 3 alphanumeric characters"

const val VALIDATION_REGEX_TRANSACTION_TYPE = "^[A-Z0-9_]{1,19}$"
const val VALIDATION_MESSAGE_TRANSACTION_TYPE = "Transaction Type must be 1-19 capital alphanumeric characters or underscores"

const val VALIDATION_REGEX_PRISONER_ID = "^[A-Za-z]\\d{4}[A-Za-z]{2}$"
const val VALIDATION_MESSAGE_PRISONER_ID = "prisonerId must be 7 alphanumeric characters"
