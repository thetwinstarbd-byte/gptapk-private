# Supabase backend

The `islamic-chat` Edge Function keeps the AI provider key on the server. It does not yet retrieve verified Quran/Hadith records.

For local development, see Supabase Edge Functions docs:
https://supabase.com/docs/guides/functions

Before public release:
- enable caller authentication and verify JWTs
- add per-user rate limits and usage accounting
- add source retrieval and only return citations retrieved from trusted datasets
- add monitoring, privacy policy, and abuse protection
