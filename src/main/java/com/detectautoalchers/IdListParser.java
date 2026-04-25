package com.detectautoalchers;

import java.util.LinkedHashSet;
import java.util.Set;

final class IdListParser
{
    private IdListParser()
    {
    }

    static Set<Integer> parse(String ids)
    {
        Set<Integer> parsed = new LinkedHashSet<>();
        if (ids == null || ids.trim().isEmpty())
        {
            return parsed;
        }

        for (String part : ids.split(","))
        {
            String value = part.trim();
            if (value.isEmpty())
            {
                continue;
            }

            try
            {
                parsed.add(Integer.parseInt(value));
            }
            catch (NumberFormatException ignored)
            {
                // Invalid config tokens are ignored so one typo does not disable the plugin.
            }
        }

        return parsed;
    }
}
