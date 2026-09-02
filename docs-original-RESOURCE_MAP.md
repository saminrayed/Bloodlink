# Resource Map

## Resource tree

```text
src/main/resources/com/bloodlink/
├── config/
│   └── application.properties
├── css/
│   └── theme.css
├── sql/
│   └── schema.sql
└── view/
    ├── admin_dashboard.fxml
    ├── donor_dashboard.fxml
    ├── login.fxml
    ├── register.fxml
    └── requester_dashboard.fxml
```

## Loading references

### FXML navigation

File: `src/main/java/com/bloodlink/util/SceneManager.java`

```java
FXMLLoader loader = new FXMLLoader(Main.class.getResource("/com/bloodlink/view/" + fxml));
```

To add another screen, place it in `src/main/resources/com/bloodlink/view/` and pass its filename to `SceneManager`.

### CSS

Every FXML root uses:

```xml
stylesheets="@../css/theme.css"
```

Keep visual styling in `theme.css`, not inline FXML styles.

### Configuration

File: `src/main/java/com/bloodlink/util/AppConfig.java`

```java
AppConfig.class.getResourceAsStream("/com/bloodlink/config/application.properties")
```

The `${ENV_NAME:default}` syntax is resolved by `AppConfig`.

### SQL

File: `src/main/java/com/bloodlink/util/DatabaseSetup.java`

```java
private static final String SCHEMA_RESOURCE = "/com/bloodlink/sql/schema.sql";
```

## Images, icons, and fonts

No external binary asset is required. The interface intentionally uses Unicode symbols and system font fallbacks, preventing missing-image or missing-font startup failures.

Optional future assets may be placed under:

```text
src/main/resources/com/bloodlink/images/
src/main/resources/com/bloodlink/icons/
src/main/resources/com/bloodlink/fonts/
```

Load an image with:

```java
getClass().getResource("/com/bloodlink/images/logo.png")
```

Do not use filesystem-relative paths such as `src/main/resources/...` at runtime.
