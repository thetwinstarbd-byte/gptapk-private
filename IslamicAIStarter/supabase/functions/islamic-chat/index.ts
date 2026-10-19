// MVP starter only. Before public release, add user authentication,
// per-user/IP rate limits, request-size limits, abuse monitoring, and source retrieval.
const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }
  if (req.method !== "POST") {
    return Response.json({ error: "POST required" }, { status: 405, headers: corsHeaders });
  }

  const apiKey = Deno.env.get("OPENAI_API_KEY");
  if (!apiKey) {
    return Response.json({ error: "AI provider key is not configured" }, { status: 500, headers: corsHeaders });
  }

  let body: { message?: string; language?: string };
  try {
    body = await req.json();
  } catch {
    return Response.json({ error: "Invalid JSON" }, { status: 400, headers: corsHeaders });
  }

  const message = (body.message ?? "").trim();
  if (!message || message.length > 4000) {
    return Response.json({ error: "Message must be 1–4000 characters" }, { status: 400, headers: corsHeaders });
  }

  const systemPrompt = `
You are an assistant for an Islamic learning app. Reply in clear Bengali unless the user asks otherwise.
Important:
- You are not a mufti and must not claim certainty when you are unsure.
- Never invent Quran verses, hadith quotations, reference numbers, grading, or scholarly citations.
- This MVP does not yet have a connected, verified Islamic retrieval database. Do not present any citation as verified.
- When a question requires a religious ruling, explain the general context carefully and recommend consulting a qualified scholar for consequential personal cases.
- If you cannot substantiate a claim, say that the source has not been verified in this app.
- Distinguish scholarly disagreement where relevant.
- Do not claim that you searched an Islamic database; it is not connected yet.
`;

  try {
    const model = Deno.env.get("OPENAI_MODEL") ?? "gpt-4o-mini";
    const response = await fetch("https://api.openai.com/v1/chat/completions", {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${apiKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        model,
        messages: [
          { role: "system", content: systemPrompt },
          { role: "user", content: message },
        ],
        temperature: 0.2,
        max_tokens: 700,
      }),
    });

    if (!response.ok) {
      const err = await response.text();
      console.error("AI provider error status:", response.status, err.slice(0, 300));
      return Response.json({ error: "AI provider request failed" }, { status: 502, headers: corsHeaders });
    }

    const data = await response.json();
    const answer = data.choices?.[0]?.message?.content ?? "দুঃখিত, এই মুহূর্তে উত্তর তৈরি করা যায়নি।";
    return Response.json({
      answer,
      references: [],
      source_status: "যাচাইকৃত ইসলামিক ডেটাবেস এখনো সংযুক্ত নয়",
      model,
    }, { headers: { ...corsHeaders, "Content-Type": "application/json" } });
  } catch (error) {
    console.error("Function error:", String(error));
    return Response.json({ error: "Unexpected server error" }, { status: 500, headers: corsHeaders });
  }
});
