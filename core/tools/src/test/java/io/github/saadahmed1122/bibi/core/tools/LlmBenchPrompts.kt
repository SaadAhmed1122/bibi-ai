package io.github.saadahmed1122.bibi.core.tools

// Pure Kotlin (no android.*).

/** One benchmark prompt: [expected] null = no action wanted ("none"). */
data class BenchPrompt(val text: String, val expected: ToolName?, val expectedCall: ToolCall?)

/**
 * The fixed LLM benchmark set: 20 prompts per action and 10 with no action. Every prompt is free
 * form, so the shortcut matcher misses it (a JVM test checks this) and it measures the LLM path.
 * Phone numbers are fictional: Ofcom drama ranges (020 7946 0xxx, 0161 496 0xxx, 07700 900xxx …)
 * and the US 555-01xx range. The set is a first guess, not a validated corpus [VERIFY].
 */
object LlmBenchPrompts {
    private fun app(text: String, app: String) = BenchPrompt(text, ToolName.OPEN_APP, ToolCall.OpenApp(app))
    private fun alarm(text: String, h: Int, m: Int) = BenchPrompt(text, ToolName.SET_ALARM, ToolCall.SetAlarm(h, m))
    private fun timer(text: String, s: Int) = BenchPrompt(text, ToolName.START_TIMER, ToolCall.StartTimer(s))
    private fun torch(text: String, on: Boolean) = BenchPrompt(text, ToolName.FLASHLIGHT, ToolCall.Flashlight(on))
    private fun dial(text: String, n: String) = BenchPrompt(text, ToolName.DIAL, ToolCall.Dial(n))
    private fun none(text: String) = BenchPrompt(text, null, null)

    val ALL: List<BenchPrompt> = listOf(
        app("bring up google maps for me", "maps"),
        app("I want to check my email in gmail", "gmail"),
        app("show me the camera app", "camera"),
        app("take me to the settings screen", "settings"),
        app("get youtube going", "youtube"),
        app("pull up my calendar", "calendar"),
        app("I'd like to look at my photos", "photos"),
        app("go to the play store", "play store"),
        app("fire up chrome", "chrome"),
        app("let me see the calculator", "calculator"),
        app("jump into whatsapp", "whatsapp"),
        app("I need the clock app", "clock"),
        app("switch over to spotify", "spotify"),
        app("I need to find a download in my files app", "files"),
        app("pop open the messages app", "messages"),
        app("show my gallery", "gallery"),
        app("get me into google drive", "drive"),
        app("I want to browse instagram", "instagram"),
        app("navigate to the notes app", "notes"),
        app("bring the weather app up", "weather"),

        alarm("I need to get up at 6:45 tomorrow", 6, 45),
        alarm("make sure I'm awake by seven thirty", 7, 30),
        alarm("alarm me at quarter past eight in the morning", 8, 15),
        alarm("I have to be up at 5 am", 5, 0),
        alarm("could you wake me at half past six", 6, 30),
        alarm("set me an alarm for 9 in the evening", 21, 0),
        alarm("ring an alarm at 22:00", 22, 0),
        alarm("wake me up at 7 tomorrow please", 7, 0),
        alarm("I'd like an alarm at 13:30 for my nap", 13, 30),
        alarm("please get me up at 4:15 for my flight", 4, 15),
        alarm("set an alarm for noon", 12, 0),
        alarm("need an alarm at 10 past 7 in the morning", 7, 10),
        alarm("an alarm at 23:45 would be great", 23, 45),
        alarm("wake-up call at 6 am please", 6, 0),
        alarm("I must not oversleep, alarm at 8:20", 8, 20),
        alarm("set the alarm for twenty to nine in the morning", 8, 40),
        alarm("alarm for 5:30 in the morning", 5, 30),
        alarm("my meeting is early, wake me at 7:05", 7, 5),
        alarm("remind me to get up at 9 am with an alarm", 9, 0),
        alarm("I want an alarm at 18:00", 18, 0),

        timer("count down ten minutes for the eggs", 600),
        timer("let me know when 3 minutes are up", 180),
        timer("time my workout for 20 minutes", 1200),
        timer("I need a 45 second countdown", 45),
        timer("begin counting down from 2 minutes", 120),
        timer("the pizza needs 12 minutes", 720),
        timer("give me a quarter of an hour on the clock", 900),
        timer("timer, one and a half hours", 5400),
        timer("remind me in 30 seconds with a timer", 30),
        timer("set a countdown for 90 minutes", 5400),
        timer("steep the tea for 4 minutes and tell me", 240),
        timer("put 25 minutes on the timer for studying", 1500),
        timer("I want a timer of one hour", 3600),
        timer("count 60 seconds for me", 60),
        timer("buzz me after 8 minutes", 480),
        timer("I need a 2 hour timer for the roast", 7200),
        timer("time 5 minutes of meditation", 300),
        timer("a timer for the laundry, 40 minutes", 2400),
        timer("please count down 15 minutes", 900),
        timer("rest timer of 90 seconds", 90),

        torch("it's too dark to see anything", true),
        torch("I can't find my keys, give me some light", true),
        torch("light up the torch", true),
        torch("kill the flashlight", false),
        torch("I don't need the light anymore, switch it off", false),
        torch("turn on the light on the back of the phone", true),
        torch("shine a light please", true),
        torch("torch please", true),
        torch("stop the flashlight", false),
        torch("lights out on the torch", false),
        torch("can I have the flashlight", true),
        torch("the torch is still on, turn it off", false),
        torch("I need the flashlight right now", true),
        torch("enough light, shut the torch", false),
        torch("power on the flashlight", true),
        torch("flashlight, please switch on", true),
        torch("disable the torch", false),
        torch("activate the flashlight", true),
        torch("I'm done, flashlight off now", false),
        torch("put the torch on so I can see", true),

        dial("get 020 7946 0018 up on the dialer", "020 7946 0018"),
        dial("I need to ring the dentist on 020 7946 0321", "020 7946 0321"),
        dial("put +44 20 7946 0102 in the phone app", "+44 20 7946 0102"),
        dial("can I call 555 0142 please", "555 0142"),
        dial("dial up my mum on 07700 900123", "07700 900123"),
        dial("please phone the office, number 020 7946 0456", "020 7946 0456"),
        dial("the number is 0161 496 0789, call it", "0161 496 0789"),
        dial("ring 0117 496 0234 for me", "0117 496 0234"),
        dial("show 555 0199 on the keypad", "555 0199"),
        dial("I want to phone +1 202 555 0147", "+1 202 555 0147"),
        dial("get the plumber on 020 7946 0987", "020 7946 0987"),
        dial("call this number for me: 0113 496 0555", "0113 496 0555"),
        dial("let me ring 07700 900456", "07700 900456"),
        dial("put a call through to 0121 496 0111", "0121 496 0111"),
        dial("dial the pharmacy at 020 7946 0222", "020 7946 0222"),
        dial("I need to call 555 0110 back", "555 0110"),
        dial("make a call to +44 7700 900789", "+44 7700 900789"),
        dial("type 0131 496 0333 into the phone", "0131 496 0333"),
        dial("ring the taxi firm, 0141 496 0444", "0141 496 0444"),
        dial("call the school on 020 7946 0666", "020 7946 0666"),

        none("what's the capital of France"),
        none("tell me a joke"),
        none("how are you today"),
        none("don't set an alarm tonight"),
        none("what time is it in Tokyo"),
        none("I had a great day at the park"),
        none("how long should I boil an egg"),
        none("who wrote Romeo and Juliet"),
        none("send a text to mum saying I'm late"),
        none("thanks Bibi, that's all"),
    )

    /**
     * Whether [actual] carries the values the prompt asked for. Lenient for app names (case,
     * spaces, one contained in the other, e.g. "maps" vs "Google Maps") and for number formatting.
     */
    fun argsMatch(expected: ToolCall, actual: ToolCall): Boolean = when {
        expected is ToolCall.OpenApp && actual is ToolCall.OpenApp -> {
            val e = expected.app.trim().lowercase()
            val a = actual.app.trim().lowercase()
            a.isNotEmpty() && (a == e || a.contains(e) || e.contains(a))
        }
        expected is ToolCall.Dial && actual is ToolCall.Dial ->
            ToolValidator.normalizeNumber(expected.number) != null &&
                ToolValidator.normalizeNumber(expected.number) == ToolValidator.normalizeNumber(actual.number)
        else -> expected == actual
    }
}
