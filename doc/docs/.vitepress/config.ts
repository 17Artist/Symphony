import { defineConfig } from 'vitepress'
import { withMermaid } from 'vitepress-plugin-mermaid'

export default withMermaid(
  defineConfig({
    title: 'Symphony',
    description: '可编程属性引擎 — 派生 / 条件 / 异步 / 全开放扩展点',
    lang: 'zh-CN',
    cleanUrls: true,
    lastUpdated: true,
    base: '/Symphony/',
    appearance: 'force-dark',

    head: [
      ['link', { rel: 'icon', type: 'image/svg+xml', href: '/Symphony/logo.svg' }]
    ],

    markdown: {
      languageAlias: {
        aria: 'javascript'
      }
    },

    themeConfig: {
      logo: '/logo.svg',
      siteTitle: 'Symphony',

      nav: [
        { text: '指南', link: '/guide/01-quick-start' },
        { text: '设计', link: '/design/01-architecture' },
        { text: 'GitHub', link: 'https://github.com/17Artist/Symphony' }
      ],

      sidebar: {
        '/guide/': [
          {
            text: '入门',
            collapsed: false,
            items: [
              { text: '快速开始', link: '/guide/01-quick-start' },
              { text: '属性配置', link: '/guide/02-attribute-config' }
            ]
          },
          {
            text: '系统配置',
            collapsed: false,
            items: [
              { text: '词条系统', link: '/guide/03-affix-config' },
              { text: '触发器', link: '/guide/04-trigger-reference' },
              { text: '技能提供者', link: '/guide/05-skill-provider-guide' },
              { text: '成长系统', link: '/guide/06-growth-config' }
            ]
          },
          {
            text: '脚本与集成',
            collapsed: false,
            items: [
              { text: 'Aria 脚本示例', link: '/guide/07-script-examples' },
              { text: '高级用法', link: '/guide/08-advanced-guide' },
              { text: '事件与 API 参考', link: '/guide/09-events-api' },
              { text: '扩展指南', link: '/guide/10-extending' }
            ]
          }
        ],
        '/design/': [
          {
            text: '架构',
            collapsed: false,
            items: [
              { text: '总体架构', link: '/design/01-architecture' },
              { text: '属性系统', link: '/design/02-attribute-system' },
              { text: '词条系统', link: '/design/03-affix-system' },
              { text: '触发器系统', link: '/design/04-trigger-system' },
              { text: '技能提供者', link: '/design/05-skill-provider' },
              { text: '成长系统', link: '/design/06-growth-system' }
            ]
          },
          {
            text: '集成',
            collapsed: false,
            items: [
              { text: '脚本集成', link: '/design/07-script-integration' },
              { text: '数据存储', link: '/design/08-data-storage' },
              { text: 'API 设计', link: '/design/09-api-design' },
              { text: '高级系统', link: '/design/10-advanced-systems' }
            ]
          }
        ]
      },

      search: {
        provider: 'local',
        options: {
          locales: {
            root: {
              translations: {
                button: { buttonText: '搜索文档', buttonAriaLabel: '搜索文档' },
                modal: {
                  noResultsText: '无相关结果',
                  resetButtonTitle: '清除查询',
                  footer: {
                    selectText: '选择',
                    navigateText: '切换',
                    closeText: '关闭'
                  }
                }
              }
            }
          }
        }
      },

      docFooter: { prev: '上一页', next: '下一页' },
      lastUpdatedText: '最后更新',
      outline: { label: '本页', level: [2, 3] },
      returnToTopLabel: '返回顶部',
      sidebarMenuLabel: '菜单'
    }
  }),
  {
    // mermaid 渲染配置：固定 dark 主题（站点强制深色）
    mermaid: {
      theme: 'dark',
      securityLevel: 'loose'
    },
    mermaidPlugin: {
      class: 'mermaid'
    }
  }
)
