/*
 * Copyright 2012-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.autoconfigure.condition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionMessage.Style;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
//import org.springframework.context.annotation.ConfigurationClassParser;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.PropertyResolver;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.Assert;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

/**
 *
 * TODO: 注意 ConditionalOnProperty 写在自动装配类上的解析时机与 ConditionOnBean, ConditionOnWebApplication , ConditionOnClass这些不太一样, ConditionOnProperty的解析是在解析配置文件的时候，判断需不需要跳过，对应的实现类里面是
 * @see ConfigurationClassParser#processConfigurationClass(org.springframework.context.annotation.ConfigurationClass, java.util.function.Predicate), 它会去判断是否需要跳过
 * TODO: 而ConditionOnBean以及ConditionOnWebApplication如果写在了自动装配类上，它的作用时机比较早，它在
 * @see org.springframework.boot.autoconfigure.AutoConfigurationImportSelector.ConfigurationClassFilter#filter(java.util.List) 这里就会进行一个fliter,从而 selectImports 就不会将它进行返回。
 *
 * {@link Condition} that checks if properties are defined in environment.
 *
 * @author Maciej Walkowiak
 * @author Phillip Webb
 * @author Stephane Nicoll
 * @author Andy Wilkinson
 * @see ConditionalOnProperty
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 40)
class OnPropertyCondition extends SpringBootCondition {

	@Override
	public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {

		// TODO: map拆成list
		List<AnnotationAttributes> allAnnotationAttributes = annotationAttributesFromMultiValueMap(
				metadata.getAllAnnotationAttributes(ConditionalOnProperty.class.getName()));
		// TODO: 创建匹配结果集
		List<ConditionMessage> noMatch = new ArrayList<>();
		List<ConditionMessage> match = new ArrayList<>();
		for (AnnotationAttributes annotationAttributes : allAnnotationAttributes) {
			// TODO: 去判断是否适配，注意这里把environment传进去了
			ConditionOutcome outcome = determineOutcome(annotationAttributes, context.getEnvironment());
			(outcome.isMatch() ? match : noMatch).add(outcome.getConditionMessage());
		}
		if (!noMatch.isEmpty()) {
			return ConditionOutcome.noMatch(ConditionMessage.of(noMatch));
		}
		return ConditionOutcome.match(ConditionMessage.of(match));
	}

	/**
	 * TODO: 进行拆分map,多值map拆分成单值map
	 *
	 * {chusen:[1,2,3],ha:[2,3,5]}
	 *   map0      map1      map2
	 * chusen:1, chusen:2, chusen:3
	 * ha:2,    ha:3,     ha:5
	 *
	 * 三个Map
	 *
	 * 最后把map转为AnnotationAttributes list 返回回去
	 *
	 * @param multiValueMap
	 * @return
	 */
	private List<AnnotationAttributes> annotationAttributesFromMultiValueMap(
			MultiValueMap<String, Object> multiValueMap) {
		List<Map<String, Object>> maps = new ArrayList<>();
		multiValueMap.forEach((key, value) -> {
			for (int i = 0; i < value.size(); i++) {
				Map<String, Object> map;
				if (i < maps.size()) {
					map = maps.get(i);
				}
				else {
					map = new HashMap<>();
					maps.add(map);
				}
				map.put(key, value.get(i));
			}
		});
		List<AnnotationAttributes> annotationAttributes = new ArrayList<>(maps.size());
		for (Map<String, Object> map : maps) {
			annotationAttributes.add(AnnotationAttributes.fromMap(map));
		}
		return annotationAttributes;
	}

	private ConditionOutcome determineOutcome(AnnotationAttributes annotationAttributes, PropertyResolver resolver) {
		// TODO: 创建了一个Spec
		Spec spec = new Spec(annotationAttributes);
		List<String> missingProperties = new ArrayList<>();
		List<String> nonMatchingProperties = new ArrayList<>();
		// TODO: 这里是关键，去匹配环境中的属性值
		spec.collectProperties(resolver, missingProperties, nonMatchingProperties);
		// TODO: 如果missingProperties 不为空，则不匹配
		if (!missingProperties.isEmpty()) {
			return ConditionOutcome.noMatch(ConditionMessage.forCondition(ConditionalOnProperty.class, spec)
					.didNotFind("property", "properties").items(Style.QUOTE, missingProperties));
		}
		// TODO: 如果nonMatchingProperties 不为空，也是不匹配的
		if (!nonMatchingProperties.isEmpty()) {
			return ConditionOutcome.noMatch(ConditionMessage.forCondition(ConditionalOnProperty.class, spec)
					.found("different value in property", "different value in properties")
					.items(Style.QUOTE, nonMatchingProperties));
		}
		// TODO: 最后走到这 就是匹配了
		return ConditionOutcome
				.match(ConditionMessage.forCondition(ConditionalOnProperty.class, spec).because("matched"));
	}

	private static class Spec {

		private final String prefix;

		private final String havingValue;

		private final String[] names;

		private final boolean matchIfMissing;

		/**
		 * TODO: 其实这里面就是取注解的属性值
		 * @param annotationAttributes
		 */
		Spec(AnnotationAttributes annotationAttributes) {
			// TODO: 拿到前缀，如果前缀没有加点，那就补上
			String prefix = annotationAttributes.getString("prefix").trim();
			if (StringUtils.hasText(prefix) && !prefix.endsWith(".")) {
				prefix = prefix + ".";
			}
			this.prefix = prefix;
			this.havingValue = annotationAttributes.getString("havingValue");
			// TODO: 收集name或者value
			this.names = getNames(annotationAttributes);
			this.matchIfMissing = annotationAttributes.getBoolean("matchIfMissing");
		}

		/**
		 * TODO: 把 ConditionalOnProperty 里面的 name或者是value收集起来
		 * @param annotationAttributes
		 * @return
		 */
		private String[] getNames(Map<String, Object> annotationAttributes) {
			String[] value = (String[]) annotationAttributes.get("value");
			String[] name = (String[]) annotationAttributes.get("name");
			Assert.state(value.length > 0 || name.length > 0,
					"The name or value attribute of @ConditionalOnProperty must be specified");
			Assert.state(value.length == 0 || name.length == 0,
					"The name and value attributes of @ConditionalOnProperty are exclusive");
			return (value.length > 0) ? value : name;
		}

		/**
		 * TODO：收集属性值 是否匹配
		 * @param resolver
		 * @param missing
		 * @param nonMatching
		 */
		private void collectProperties(PropertyResolver resolver, List<String> missing, List<String> nonMatching) {
			// TODO: 把所有的待匹配的值列出来 进行遍历
			for (String name : this.names) {
				// TODO: 把前缀加上
				String key = this.prefix + name;
				// TODO: 看看当前环境中有没有这个属性
				if (resolver.containsProperty(key)) {
					// TODO: 如果环境中的当前key对应的属性值与给定的havingValue不匹配，那就加到nonMatching里面去
					if (!isMatch(resolver.getProperty(key), this.havingValue)) {
						nonMatching.add(name);
					}
				}
				else {
					// TODO: 如果这个属性没有，看看是否应该匹配，默认false
					// TODO: 默认false,如果缺失了 也算它不匹配
					if (!this.matchIfMissing) {
						missing.add(name);
					}
				}
			}
		}

		/**
		 * TODO: 判断value是否匹配
		 *
		 * @param value
		 * @param requiredValue
		 * @return
		 */
		private boolean isMatch(String value, String requiredValue) {
			// TODO: 如果requiredValue有值，则判断两个值是否相等
			if (StringUtils.hasLength(requiredValue)) {
				return requiredValue.equalsIgnoreCase(value);
			}
			// TODO: 否则判断是不是不等于false, 如果当前属性值为true,那么也匹配
			return !"false".equalsIgnoreCase(value);
		}

		@Override
		public String toString() {
			StringBuilder result = new StringBuilder();
			result.append("(");
			result.append(this.prefix);
			if (this.names.length == 1) {
				result.append(this.names[0]);
			}
			else {
				result.append("[");
				result.append(StringUtils.arrayToCommaDelimitedString(this.names));
				result.append("]");
			}
			if (StringUtils.hasLength(this.havingValue)) {
				result.append("=").append(this.havingValue);
			}
			result.append(")");
			return result.toString();
		}

	}

}
